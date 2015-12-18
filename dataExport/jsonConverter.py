#! /usr/bin/python
import schemas
import json
import os
import splitAndCompress
import sys
import codecs
import re

def convert(fileName, schemaName, preserveOriginal):
	schema = schemas.schemas[schemaName]['columns']
	suppressedColumns = None
	if 'suppressed_columns' in schemas.schemas[schemaName]:
		suppressedColumns = schemas.schemas[schemaName]['suppressed_columns']
	
	jsonFileName = fileName + ".json"
	jsonFile = open(jsonFileName, "w+")
	
	file = codecs.open(fileName, "r", "utf-8")
	numCols = len(schema)
	row = dict()
	trueInd = 0
	hasNext = False

	for line in file:
		cols = line.split("\t~")
		details = None
		for ind in range(len(cols)):
			val = cols[ind].strip()
			if ( ind == 0 and trueInd != 0):
				trueInd -= 1
				if schema[trueInd] in row:
					val = row[schema[trueInd]] + unicode("\n") + unicode(val)
			
			# If the value is null, then skip it
			if val == 'NULL':
				trueInd += 1
				continue
			
			# Skip suppressed columns
			if suppressedColumns != None and schema[trueInd] in suppressedColumns:
				trueInd += 1
				continue
			
			# If the value is a json object (event details), then parse it out, and apply it at the end
			if ( len(val) > 1 and val[0] == '{' and val[-1] == '}' and ':' in val):
				details = json.loads(val, encoding='utf-8')
			else:
				# print val
				row[schema[trueInd]] = unicode(val)
			
			trueInd += 1
		
		# Apply event details to the row, to flatten it
		if details != None:
			for key, value in details.iteritems():
				if ( value != None ):
					row[key] = unicode(value)
		
		if trueInd == numCols:
			jsonStr = json.dumps(row, ensure_ascii=False, encoding='utf-8')
			jsonFile.write(jsonStr.encode('utf-8'))
			jsonFile.write("\n")
			
			trueInd = 0
			row = dict()
		
	jsonFile.close()
	
	if not preserveOriginal:
		os.remove(fileName)
		os.rename(jsonFileName, fileName)
			
		splitAndCompress.splitAndCompress([fileName])
	
if __name__ == "__main__":
	# convert('saved_searches_alerts_settings.txt', 'saved_searches_alerts_settings', preserveOriginal=True)
	# convert('saved_searches.txt', 'saved_searches', preserveOriginal=True)
	convert(sys.argv[1], 'inquiries', False)

