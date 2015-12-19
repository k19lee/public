var express = require('express'),
	bodyParser = require('body-parser'),
	constants = require('./constants'),
	cookieParser = require('cookie-parser'),
	fs = require('fs'),
	
	RouteHandler = require('./utils/routeHandler');
	
var app = express();
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({
	extended: true
}));
app.use(cookieParser());

var routeHandler = new RouteHandler(app);
// Loop through all the controllers, and register that paths and handlers
// Each controller should define a "getPaths" method, that returns an object
//   that defines what it listens to and what it handles
 
fs.readdirSync('./controllers').forEach(function(file, index) {
	var pageController = require('./controllers/' + file);
	pageController = new pageController();
	if ( typeof pageController.getPaths === 'function' ) { 
		var paths = pageController.getPaths();
		if ( paths != null ) {
			for ( var path in paths ) {
			
				// Add an implicit "post" handler for every url. This allows for
				//   lock operations on every page
				paths[path].post = paths[path].post || () => {};
			
				for ( var method in paths[path] ) {
					routeHandler.add(method, path, paths[path][method].bind(pageController));
				}
			}
		}
	}
});

// Expose the screenshot directory as accessible
app.use(express.static(constants.screenshotDir));

app.listen(8888, function() {
	console.log('Listening on port 8888...')
});
