#! /usr/bin/python
import run_sql
import schemas
import datetime
import glob
import time

def webevents_runner(startDate, endDate, increment):
    
    process_limit = 6
    
    start = startDate
    while start < endDate: 
        
        # .txt files are ones still being processed
        subFiles = glob.glob("*.txt")
        while ( len(subFiles) > process_limit ):
            print "Waiting for files to finish before launching more"
            time.sleep(60)
            subFiles = glob.glob("*.txt")
        
        end = start + increment
        if end > endDate:
            end = endDate
        
        sqlQuery = "select * from webevents where day >= '{0}-{1}-{2}' and day <= '{3}-{4}-{5}'"\
            .format(start.year, start.month, start.day, end.year, end.month, end.day)
        sqlQuery = sqlQuery + " and event_timestamp >= '{0}-{1}-{2} {3}:{4}:{5}' and event_timestamp < '{6}-{7}-{8} {9}:{10}:{11}'"\
            .format(start.year, start.month, start.day, start.hour, start.minute, start.second, \
                    end.year, end.month, end.day, end.hour, end.minute, end.second)
        print "Running {0}".format(sqlQuery)
        run_sql.run(sqlQuery, "webevents", "webevents-{0}-{1}-{2}_{3}_{4}_{5}.txt".format(start.year, start.month, start.day, start.hour, start.minute, start.second))
        
        start = end

if __name__ == "__main__":
    webevents_runner(datetime.datetime(2015, 10, 26, 6, 0, 0), datetime.datetime(2015,11,1,0,0,0), datetime.timedelta(hours=1))
