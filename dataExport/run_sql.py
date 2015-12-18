#! /usr/bin/python
import os
import subprocess
import jsonConverter
import schemas
import multiprocessing

def run(sql, schema, output, preserveOriginal=False):
	
	# Fill in credentials
	if schemas.schemas[schema]['host'] == 'redshift':
		host = ""
		port = ""
		db = ""
		user = ""
		password = ""
	elif schemas.schemas[schema]['host'] == 'stingray':
		host = ""
		port = ""
		db = ""
		user = ""
		password = ""
		
	os.environ["PGPASSWORD"] = password
	
	print "Processing {0}".format(output)
	subprocess.call(["/usr/local/bin/psql",
		  "-U", user,
		  "-h", host,
		  "-d", db,
		  "-c", sql,
		  "-o", output,
		  "-p", port,
		  "-P", "border=0",
		  "-P", "format=unaligned",
		  "-P", "tuples_only",
		  "-P", "fieldsep=	~",
		  "-P", "null=NULL"])
	
	print "\tConverting to JSON"
	p = multiprocessing.Process(target=jsonConverter.convert, args=(output, schema, preserveOriginal))
	p.start()
	
	print "Finished {0}".format(output)
	
if __name__ == "__main__":
   # run("select * from webevents where day = '2015-10-01' limit 10", "webevents", "test_webevents.txt")
   run("select * from inquiries", "inquiries", "inquiries.txt")
   # run("select * from logins", "logins", "logins.txt")
   # run("select * from saved_searches", "saved_searches", "saved_searches.txt")
   # run("select * from saved_searches_alerts_settings", "saved_searches_alerts_settings", "saved_searches_alerts_settings.txt")

