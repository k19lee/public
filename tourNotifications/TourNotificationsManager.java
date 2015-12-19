package com.redfin.alerts;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.now.Now;
import com.google.api.services.now.model.*;
import com.google.api.services.now.model.Image;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import redfin.core.domain.*;
import redfin.core.domain.helper.ListingPhotoHelper;
import redfin.dataAccess.interfaces.*;
import redfin.guice.ReadOnly;
import redfin.guice.ReadWrite;
import redfin.guice.TransactionalSimple;
import redfin.logwriter.RiftLogWriter;
import redfin.serverPaths.ServerPaths;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TourNotificationsManager {
    
    private static final Logger logger = Logger.getLogger(TourNotificationsManager.class);
    
    private SavedSearchesAlertsRun run;
    private TourNotificationsConfiguration config;
    
    @Inject @ReadWrite private SavedSearchesAlertsRunDAO runDAO;
    @Inject @ReadOnly private TourAppointmentDAO tourAppointmentDAO;
    @Inject @ReadOnly private GoogleUserDAO googleUserDAO;
    @Inject @ReadWrite private GoogleUserDAO googleUserRWDAO;
    @Inject @ReadOnly private ListingPhotoDAO listingPhotoDAO;
    @Inject @ReadOnly private LoginDAO loginDAO;
    
    @Inject Configuration stingrayConfig;
    @Inject RiftLogWriter riftLogWriter;
    
    @Inject ServerPaths serverPaths;
    
    // Since Google Now cards only work with prod image urls, we need to override these 
    //   configuration values to always be the prod image server
    private String photoServerBaseUrl;
    private String imageServerBaseUrl; 
    
    // The universal prefix for the tap action on cards. Deep links to android tours page
    public final String ACTION_PREFIX = "android-app://com.redfin.android/https/redfin.com/myredfin/tours?tourId=";
    
    public void init(SavedSearchesAlertsRun run, TourNotificationsConfiguration config) {
        this.run = run;
        this.config = config;
        
        if ( "prod".equals(stingrayConfig.getString("environmentName")) ) {
            photoServerBaseUrl = stingrayConfig.getString("mlsPhotoServerBaseUrl");
            imageServerBaseUrl = serverPaths.getSecureServerPaths().get(ServerPaths.IMAGE_SERVER_KEY);
        } else {
            // Force prod image urls because of Google image proxy.
            // Is there a way to not have to hard code this?
            photoServerBaseUrl = "https://ssl.cdn-redfin.com/photo";
            imageServerBaseUrl = "https://ssl.cdn-redfin.com/vLATEST";
        }
    }
    
    public void processUpcomingTours() {
        
        if ( !config.isRegularFlow() ) {
            processSpecialOperations();
            return;
        }
        
        // Matching
        List<TourAppointmentNowCard> upcomingTours = null;
        if ( config.isMatchingEnabled() ) {
            upcomingTours = findModifiedTours();
        }
        
        run.setMatchingFinishedTime(new Date());
        run.setFormattingStartTime(new Date());
        saveRun(run);
        
        // Formatting
        if ( config.isFormattingEnabled() ) {
            formatTours(upcomingTours);
        }
        
        run.setFormattingFinishedTime(new Date());
        run.setSendingStartTime(new Date());
        saveRun(run);
        
        // Sending
        if ( config.isSendingEnabled() ) {
            sendTourCards(upcomingTours);
        }
        
        run.setSendingFinishedTime(new Date());
        saveRun(run);
    }
    
    // For debugging / testability
    @TransactionalSimple
    public void processSpecialOperations() {
        try {
            if ( config.getSingleUserEmail() == null ) {
                logger.error("Unable to perform special operation without a singleUserEmail");
                return;
            }
            
            Login login = loginDAO.findByEmail(config.getSingleUserEmail());
            if ( login == null ) {
                logger.error("Unable to find login for email: " + config.getSingleUserEmail());
                return;
            }
            
            GoogleUser gu = googleUserDAO.getByLogin(login);
            if ( gu == null || gu.getGoogleNowRefreshToken() == null ) {
                logger.error("Login does not have a Google Now refresh token. Use the Android app to get one");
                return;
            }
            
            Now n = getNowApi(gu.getGoogleNowRefreshToken());
            
            if ( config.getOperation().equalsIgnoreCase("DELETEALL")) {
                logger.info("Deleting all cards");
                deleteAllCards(n);
            } else if ( config.getOperation().equalsIgnoreCase("LIST")) {
                logger.info("Listing all cards");
                listCards(n);
            }
        } catch( Exception ex ) {
            logger.error("Error performing operation: " + config.getOperation(), ex);
        }
        
        // Fill in the rest of the fields for completeness
        run.setMatchingFinishedTime(new Date());
        run.setFormattingStartTime(new Date());
        run.setFormattingFinishedTime(new Date());
        run.setSendingStartTime(new Date());
        run.setSendingFinishedTime(new Date());
        saveRun(run);
    }
    
    @TransactionalSimple
    void saveRun(SavedSearchesAlertsRun run) {
        runDAO.save(run);
    }
    
    @TransactionalSimple
    public List<TourAppointmentNowCard> findModifiedTours() {
        List<TourAppointment> toursToday = tourAppointmentDAO.findWithToursUpdatedBetween(
                config.getTimeSliceStart().getTime(),
                config.getTimeSliceEnd().getTime(),
                ImmutableList.of(TourAppointmentStatus.SCHEDULED, TourAppointmentStatus.COMPLETED, TourAppointmentStatus.CANCELLED, TourAppointmentStatus.CANCELLED_WITHOUT_PAY),
                ImmutableList.of("tour.login",
                                 "tour.tourItems",
                                 "tour.tourItems.listing",
                                 "agent",
                                 "agent.person"
                                 ));
        
        // The result here is multiplied by the number of tourItems in each appointment due to the join on tourItems.
        // Unique the list
        Set<TourAppointment> uniqueToursToday = Sets.newHashSet();
        uniqueToursToday.addAll(toursToday);
        
        logger.info("Found " + uniqueToursToday.size() + " tour appointments for tours modified between start and end timeslice");
        
        // A tour can have multiple tour appointments (like if one gets cancelled and another is scheduled)
        // For each tour, only keep the most recent appointment.
        // This also prevents there from legitimately being two appointments for the same tour (is this common?)
        Map<Long, TourAppointment> appointmentsByTourId = Maps.newHashMap();
        for ( TourAppointment ta : uniqueToursToday ) {
            if ( appointmentsByTourId.containsKey(ta.getTour().getId()) ) {
                // put in the appointment with the later createdDate
                TourAppointment other = appointmentsByTourId.get(ta.getTour().getId());
                if ( ta.getCreatedDate().after(other.getCreatedDate()) ) {
                    appointmentsByTourId.put(ta.getTour().getId(), ta);
                }
            } else {
                appointmentsByTourId.put(ta.getTour().getId(), ta);
            }
        }
        
        toursToday.clear();
        toursToday.addAll(appointmentsByTourId.values());
        
        logger.info("Found " + toursToday.size() + " tours modified between start and end timeslice");
        if ( config.getSingleUserEmail() != null ) {
            for ( int i = 0; i < toursToday.size(); i++ ) {
                if ( ! config.getSingleUserEmail().equals(toursToday.get(i).getTour().getLogin().getPrimaryEmail()) ) {
                    toursToday.remove(i);
                    i--;
                }
            }
            logger.info("Found " + toursToday.size() + " tours matching singleUserEmail");
        }
        if ( config.getWhiteListedEmails() != null && !config.getWhiteListedEmails().isEmpty() ) {
            logger.info("Filtering results based on whitelisted emails: " + config.getWhiteListedEmails());
            for ( int i = 0; i < toursToday.size(); i++ ) {
                if ( !config.getWhiteListedEmails().contains(toursToday.get(i).getTour().getLogin().getPrimaryEmail()) ) {
                    toursToday.remove(i);
                    i--;
                }
            }
            logger.info("Found " + toursToday.size() + " tours matching whitelisted emails");
        }
        
        // Look for tours that have logins with a GoogleUser entry
        Set<Login> uniqueLogins = Sets.newHashSet();
        for ( TourAppointment ta : toursToday ) {
            uniqueLogins.add(ta.getTour().getLogin());
        }
        Map<Login, GoogleUser> loginToGoogleUser = Maps.newHashMap();
        List<GoogleUser> uniqueGoogleUsers = googleUserDAO.getByLogins(uniqueLogins);
        for ( GoogleUser gu : uniqueGoogleUsers ) {
            loginToGoogleUser.put(gu.getLogin(), gu);
        }
        logger.info("Found " + loginToGoogleUser.size() + " google users");
        
        List<TourAppointmentNowCard> tourCards = Lists.newArrayList();
        
        // There are very few logins that have a GoogleUser row, so we can short circuit things here
        if ( loginToGoogleUser.size() == 0 ) {
            return tourCards;
        }
        
        for ( int i = 0; i < toursToday.size(); i++ ) {
            TourAppointment ta = toursToday.get(i);
            
            // Filter out tours in which the login doesn't have a refresh token
            GoogleUser gu = loginToGoogleUser.get(ta.getTour().getLogin());
            if ( gu == null || gu.getGoogleNowRefreshToken() == null ) {
                toursToday.remove(i);
                i--;
                continue;
            }
        }
        logger.info("Found " + toursToday.size() + " google users with Now refresh tokens");
        
        // At this point, we're done filtering. We need to fetch detailed information about each tour
        // Batch up a call to get the primary listing photos for each listing. This avoids having to
        //   retrieve every photo for each listing
        
        List<Listing> listings = Lists.newArrayList();
        for ( int i = 0; i < toursToday.size(); i++ ) {
            TourAppointment ta = toursToday.get(i);
            boolean hasNullListingId = false;
            
            for ( TourItem ti : ta.getTour().getTourItems() ) {
                if ( ti.getListing() == null ) {
                    hasNullListingId = true;
                    break;
                } else {
                    listings.add(ti.getListing());
                }
            }
            
            // Don't process tours that have items with a null listing_id (non-MLS homes)
            // We don't have a good way to represent them in the card, so just skip them for now
            if ( hasNullListingId ) {
                toursToday.remove(i);
                i--;
            }
        }
        Map<Listing, ListingPhoto> primaryListingPhotos = listingPhotoDAO.findPrimaryListingPhotos(listings);
        
        // Construct the return object. This shouldn't launch any further queries
        for ( TourAppointment ta : toursToday ) {
            TourAppointmentNowCard card = new TourAppointmentNowCard();
            
            card.setRecipientRefreshToken(loginToGoogleUser.get(ta.getTour().getLogin()).getGoogleNowRefreshToken());
            card.setAppointment(ta);
            
            // We got the listing photos for every listing in every tour.
            // We need to filter these to just the listings in this TourAppointment
            Map<Listing, ListingPhoto> thisToursPhotos = Maps.newHashMap();
            for ( TourItem ti : ta.getTour().getItems() ) {
                if ( primaryListingPhotos.get(ti.getListing()) != null ) {
                    thisToursPhotos.put(ti.getListing(), primaryListingPhotos.get(ti.getListing()));
                }
            }
            card.setCardPhotos(thisToursPhotos);
            tourCards.add(card);
        }
        return tourCards;
    }
    
    /**
     * This fills in the "card" field of the TourAppointmentNowCards passed in
     * @param tourCards
     */
    public void formatTours(List<TourAppointmentNowCard> tourCards) {
        
        for ( TourAppointmentNowCard tourCard : tourCards ) {
            TourAppointment ta = tourCard.getAppointment();
            logger.info("Formatting Tour Appointment for tourId: " + ta.getTour().getId());
            
            List<TourItem> tourItems = Lists.newArrayList();
            for ( TourItem ti : ta.getTour().getTourItems() ) {
                if ( !ti.getDeleted() ) {
                    tourItems.add(ti);
                }
            }
            
            Card card = new Card();
            if ( tourItems.size() == 1 ) {
                card.setContent(formatSingleTourCard(tourCard, tourItems.get(0)));
            } else if ( tourItems.size() > 1 ){
                card.setContent(formatListTourCard(tourCard, tourItems));
            }
            
            if ( Boolean.TRUE.equals(config.getForceShow()) ) {
                // Testing case. Make the card show up now
                Long oneDay = 1000L * 60 * 60 * 24;
                card.setContexts(getContexts(new Date(new Date().getTime() - oneDay),
                        new Date(new Date().getTime() + oneDay)));
            } else {
                // Start 6 hours before the tour start time. End 2 hours after the tour end time.
                card.setContexts(getContexts(new Date(ta.getStartTime().getTime() - (1000 * 60 * 60 * 6)),
                        new Date(ta.getEndTime().getTime() + (1000 * 60 * 60 * 2))));
            }
            tourCard.setCard(card);
        }
    }
    
    public CardContent formatSingleTourCard(TourAppointmentNowCard tc, TourItem tourItem) {
        CardContent cc = new CardContent();
        cc.setLocales(ImmutableList.of("en_US"));
        cc.setJustification(new TemplatedString().setDisplayString("You booked this tour with Redfin"));
        
        GenericCard card = new GenericCard();
        card.setTitle(new TemplatedString().setDisplayString("Redfin tour at " + shortTimeString(tc.getAppointment().getStartTime())));
        card.setContent(new TemplatedString().setDisplayString("Redfin Agent " + tc.getAppointment().getAgent().getPerson().getFirstNameLastName()));
        card.setLogo(new Image().setUrl(
                imageServerBaseUrl +
                "/images/logos/redfin_square_logo.png"));
        
        if ( tc.getCardPhotos() != null && tc.getCardPhotos().containsKey(tourItem.getListing()) ) {
            card.setImage(new Image().setUrl(
                    photoServerBaseUrl +
                    ListingPhotoHelper.URL_SEPARATOR +
                    ListingPhotoHelper.calculateRelativeRedfinPhotoUrl(
                            tc.getCardPhotos().get(tourItem.getListing()),
                            ListingPhotoType.FULL_SIZE)));
        } else {
            // If there's no image, intentionally leave it out
        }
        
        Address address = new Address();
        address.setName(tourItem.getListing().getLocation());
        String streetString = tourItem.getListing().getAddress().getFullStreetStringPretty();
        if ( StringUtils.isEmpty(StringUtils.trim(streetString)) ) {
            streetString = "Unknown";
        }
        address.setAddressLines(ImmutableList.of(
                streetString,
                tourItem.getListing().getAddress().getCityZipString()));
        
        card.setAddress(address);
        
        Action tapAction = new Action();
        tapAction.setUrls(ImmutableList.of(
                ACTION_PREFIX + tc.getAppointment().getTour().getId() + "&itemId=" + tourItem.getId(),
                stingrayConfig.getString("webServer.secure") + "/myredfin/tours"));
        card.setTapAction(tapAction);
        
        cc.setGenericCard(card);
        return cc;
    }
    
    public CardContent formatListTourCard(TourAppointmentNowCard tc, List<TourItem> tourItems) {
        CardContent cc = new CardContent();
        cc.setLocales(ImmutableList.of("en_US"));
        cc.setJustification(new TemplatedString().setDisplayString("You booked this tour with Redfin"));
        
        ListCard listCard = new ListCard();
        listCard.setTitle(new TemplatedString().setDisplayString("Redfin tour at " + shortTimeString(tc.getAppointment().getStartTime())));
        listCard.setLogo(new Image().setUrl(
                imageServerBaseUrl +
                "/images/logos/redfin_square_logo.png"));
        
        List<ListItem> listItems = Lists.newArrayList();
        // The ListCard can only have a maximum of 3 items.
        for ( int i = 0; i < 3 && i < tourItems.size(); i++ ) {
            TourItem ti = tourItems.get(i);
            
            ListItem item = new ListItem();
            String streetString = ti.getListing().getAddress().getFullStreetStringPretty();
            if ( StringUtils.isEmpty(StringUtils.trim(streetString)) ) {
                streetString = "Unknown";
            }
            item.setTitle(new TemplatedString().setDisplayString(streetString));
            item.setSubtitle(new TemplatedString().setDisplayString(ti.getListing().getAddress().getCityZipString()));

            TemplatedString detail1 = new TemplatedString().setDisplayString(ti.getListing().getNumBedrooms() + " beds, " +
                    ti.getListing().getNumBathrooms() + " baths - " +
                    formatNumber(ti.getListing().getApproxSqFt()) + " sq ft");
            TemplatedString detail2 = new TemplatedString().setDisplayString(
                    "$" + formatNumber(ti.getListing().getListingPrice()));
            item.setDetails(ImmutableList.of(detail1, detail2));
            
            
            if ( tc.getCardPhotos() != null && tc.getCardPhotos().containsKey(ti.getListing()) ) {
                item.setImage(new Image().setUrl(
                        photoServerBaseUrl +
                        ListingPhotoHelper.URL_SEPARATOR +
                        ListingPhotoHelper.calculateRelativeRedfinPhotoUrl(
                                tc.getCardPhotos().get(ti.getListing()),
                                ListingPhotoType.FULL_SIZE)));
            } else {
                item.setImage(new Image().setUrl(
                        imageServerBaseUrl +
                        "/images/no_photo_available_large.png"));
            }
            
            Action tapAction = new Action();
            tapAction.setUrls(ImmutableList.of(
                    ACTION_PREFIX + tc.getAppointment().getTour().getId() + "&itemId=" + ti.getId(),
                    stingrayConfig.getString("webServer.secure") + "/myredfin/tours"));
            item.setTapAction(tapAction);
            listItems.add(item);
        }
        
        // Add a "View more" button if there are more
        if ( tourItems.size() > 3 ) {
            Button b = new Button();
            b.setName("View more homes");
            
            Action tapAction = new Action();
            tapAction.setUrls(ImmutableList.of(
                    ACTION_PREFIX + tc.getAppointment().getTour().getId(),
                    stingrayConfig.getString("webServer.secure") + "/myredfin/tours"));
            b.setTapAction(tapAction);
            
            listCard.setButton(b);
        }
        
        listCard.setListItems(listItems);
        
        cc.setListCard(listCard);
        return cc;
    }
    
    private String shortTimeString(Date d) {
        return new SimpleDateFormat("h:mm a").format(d);
    }
    
    private String formatNumber(Long number) {
        if ( number == null ) {
            return "";
        }
        return new DecimalFormat("###,###,###").format(number);
    }
    
    public void sendTourCards(List<TourAppointmentNowCard> tourCards) {
        
        if ( tourCards == null || tourCards.isEmpty() ) {
            return;
        }
        
        riftLogWriter.init("tourCards."+run.getId().toString());
        
        for ( TourAppointmentNowCard card : tourCards ) {
            // Skip completed tours. Let them expire on their own
            if ( TourAppointmentStatus.COMPLETED.equals(card.getAppointment().getStatus()) ) {
                logger.info("Ignoring completed tour");
                continue;
            }
            
            Now n = null;
            try {
                n = getNowApi(card.getRecipientRefreshToken());
            } catch( Exception ex ) {
                logger.error("Unable to initialize Now API", ex);
                return;
            }
            
            // Send the Google Now requests (which includes obtaining an access token from the refresh token)
            try {
                // Look for an existing card for the same tour
                Card existingCard = findMatchingCard(n, card.getCard());
                if ( existingCard == null ) {
                    if ( TourAppointmentStatus.getCanceledStatusIds().contains(card.getAppointment().getStatus().getId()) ) {
                        // Nothing to delete.
                        logger.info("Ignoring cancelled tour");
                    } else {
                        // If we didn't find it, then create a new card
                        createCard(n, card.getCard());
                    }
                } else {
                    // If we did find it, then update it to have the new information
                    if ( TourAppointmentStatus.getCanceledStatusIds().contains(card.getAppointment().getStatus().getId()) ) {
                        // The new appointment is cancelled. Delete the card.
                        deleteCard(n, existingCard.getCardId());
                    } else {
                        // Otherwise, update the card with the new appointment's information
                        updateCard(n, existingCard, card.getCard());
                    }
                }
            } catch( TokenResponseException excep ) {
                if ( excep.getMessage() != null && excep.getMessage().indexOf("invalid_grant") != -1 ) {
                    // If we get a response saying that our refresh token is invalid, delete it from the
                    //   database, and let the Android app fetch a new one.
                    logger.info("Disabling refresh token for login: " + card.getAppointment().getTour().getLogin().getId());
                    disableRefreshToken(card.getAppointment().getTour().getLogin());
                }
                continue;
            } catch( IOException ioex ) {
                if (ioex.getMessage() != null && ioex.getMessage().indexOf("Card is a duplicate of card") != -1) {
                    logger.warn("Duplicate card found when creating card for login: "
                            + card.getAppointment().getTour().getLogin().getId(), ioex);
                } else {
                    logger.error(
                            "Error creating card for login: " + card.getAppointment().getTour().getLogin().getId(),
                            ioex);
                }
                continue;
            }
            
            // Log a rift event
            RiftEvent sentCardEvent = new RiftEvent(RiftConstants.RiftEnvironment.PUSH_SEND.toString(),
                    "google_now", RiftConstants.RiftAction.SEND_EVENT);
            sentCardEvent.setLoginId(card.getAppointment().getTour().getLogin().getId());
            Map<String,Object> details = Maps.newHashMap();
            details.put("tour_id", card.getAppointment().getTour().getId().toString());
            sentCardEvent.setEventDetails(details);
            
            riftLogWriter.queueForBatch(sentCardEvent);
        }
        riftLogWriter.flushBatchQueueAndClose();
    }
    
    protected Now getNowApi(String refreshToken) throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = new JacksonFactory();
        String clientSecretsFile = "/google_now_client_secrets.dev.json";
        if ( "prod".equals(stingrayConfig.getProperty("environmentName")) ) {
            clientSecretsFile = "/google_now_client_secrets.prod.json";
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                jsonFactory, new InputStreamReader(
                        TourNotificationsManager.class.getResourceAsStream(clientSecretsFile)));
        GoogleCredential cr = new GoogleCredential.Builder()
            .setTransport(httpTransport)
            .setJsonFactory(jsonFactory)
            .setClientSecrets(clientSecrets)
            .addRefreshListener(new CredentialRefreshListener() {
                @Override
                public void onTokenResponse(Credential credential, TokenResponse tokenResponse) throws IOException {
                    // It's okay to ask for a new access token every time. Access tokens only last an hour, and we
                    //   probably won't be sending multiple cards to the same user in that time period.
                    logger.info("Refresh token used to get a new access token : " + tokenResponse.getAccessToken());
                }
                @Override
                public void onTokenErrorResponse(Credential credential, TokenErrorResponse tokenErrorResponse)
                        throws IOException {
                    logger.info("Error with refreshing token: " + tokenErrorResponse.getError() + " : " + tokenErrorResponse.getErrorDescription());
                }
            })
            .build()
            .setRefreshToken(refreshToken);
        
        Now n = new Now.Builder(httpTransport, jsonFactory, cr)
            .setApplicationName("Redfin")
            .build();
        return n;
    }
    
    private void createCard(Now n, Card c) throws IOException {
        Card resp = n.users().cards().create("me", c).execute();
        
        logger.info("Created card: " + resp.toPrettyString());
    }
    
    private CardContexts getContexts(Date start, Date end) {
        CardContexts contexts = new CardContexts();
        
        Context timeContext = new Context();
        
          TimeRange timeRange = new TimeRange();
          
            Timestamp startTime = new Timestamp();
            if ( start == null ) {
                startTime.setSeconds(new Date().getTime() / 1000);
            } else {
                startTime.setSeconds(start.getTime() / 1000);
            }
            startTime.setNanos(0);
            
            Timestamp endTime = new Timestamp();
            if ( end == null ) {
                endTime.setSeconds(new DateTime().plusDays(5).getMillis() / 1000);
            } else {
                endTime.setSeconds(end.getTime() / 1000);
            }
            endTime.setNanos(0);
            
          timeRange.setStartTime(startTime);
          timeRange.setEndTime(endTime);
        
        timeContext.setTimeRange(timeRange);
        
        contexts.setInlineContexts(ImmutableList.of(timeContext));
        return contexts;
    }
    /////////////////
    // List cards API
    /////////////////
    private void listCards(Now n) throws IOException {
        logger.info("List cards");
        ListCardsResponse resp = n.users().cards().list("me").execute();
        List<Card> respCards = resp.getCards();
        if ( respCards == null ) {
            logger.info("LIST CARDS: Zero cards found");
        } else {
            logger.info("LIST CARDS: " + respCards.size() + " cards found");
            for ( Card c : respCards ) {
                logger.info(c.toPrettyString());
            }
        }
    }
    
    private void deleteCard(Now n, String cardId) throws IOException {
        n.users().cards().delete("me", cardId).execute();
        logger.info("Deleted: " + cardId);
    }
    
    private Card findMatchingCard(Now n, Card cardToFind) throws IOException {
        String tourIdToFind = extractTourIdFromCard(cardToFind);
        if ( tourIdToFind == null ) {
            return null;
        }
        
        // Get a list of the user's cards
        ListCardsResponse resp = n.users().cards().list("me").execute();
        List<Card> respCards = resp.getCards();
        
        if ( respCards == null ) {
            // No cards found
            return null;
        } else {
            for ( Card c : respCards ) {
                String tourId = extractTourIdFromCard(c);
                if ( tourIdToFind.equals(tourId) ) {
                    return c;
                }
            }
        }
        
        return null;
    }
    
    private String extractTourIdFromCard(Card c) {
        if ( c != null ) {
            CardContent cc = c.getContent();
            List<String> urls = null;
            // Single home card
            if ( cc.getGenericCard() != null && 
                 cc.getGenericCard().getTapAction() != null ) {
                urls = cc.getGenericCard().getTapAction().getUrls();
            }
            // List card.
            else if ( cc.getListCard() != null &&
                      cc.getListCard().getListItems() != null ) {
                for ( ListItem li : cc.getListCard().getListItems() ) {
                    // Only get the tap action for the first list item. They should all reference the same tourId
                    if ( li.getTapAction() != null ) {
                        urls = li.getTapAction().getUrls();
                        break;
                    }
                }
            }
            
            if ( urls != null ) {
                for ( String url : urls ) {
                    if ( url.startsWith(ACTION_PREFIX) ) {
                        // Take the action and strip off the prefix.
                        // Then, split the string on non-digit characters, so that the first token should be the tourId
                        String suffix = url.substring(ACTION_PREFIX.length());
                        String [] digitOnly = suffix.split("\\D", 2);
                        if ( digitOnly.length > 0 ) {
                            return digitOnly[0];
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private void updateCard(Now n, Card existingCard, Card newCard) throws IOException {
        // The api requires the new card to share the same card id
        newCard.setCardId(existingCard.getCardId());
        
        Card updatedCard = n.users().cards().update("me", existingCard.getCardId(), newCard).execute();
        logger.info("Updated card: " + updatedCard.toPrettyString());
    }
    
    private void deleteAllCards(Now n) throws IOException {
        ListCardsResponse resp = n.users().cards().list("me").execute();
        List<Card> respCards = resp.getCards();
        if ( respCards != null ) {
            for ( Card c : respCards ) {
                deleteCard(n, c.getCardId());
            }
        }
    }
    
    @TransactionalSimple
    public void disableRefreshToken(Login login) {
        if ( login == null ) {
            return;
        }
        GoogleUser gu = googleUserRWDAO.getByLogin(login);
        if ( gu != null ) {
            gu.setGoogleNowRefreshToken(null);
            googleUserRWDAO.save(gu);
        }
    }
}