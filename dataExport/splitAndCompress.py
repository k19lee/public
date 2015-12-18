#!/usr/bin/python
import sys
import os
import glob
import subprocess

def splitAndCompress(fileList):
    for file in fileList:
        print "Splitting " + file
        subprocess.call(["/usr/bin/split", "-l", "2000000", "-a", "1", file, file[:file.index('.')] + "_part_"])
        
        subFiles = glob.glob(file[:file.index('.')] + "_part_*")
        for subFile in subFiles:
            print "Compressing " + subFile
            subprocess.call(["/usr/local/bin/lzop", "-U", subFile])
        
        os.unlink(file)
        
        s3UploadCmd = [
            "s3cmd",
            "--access_key=",
            "--secret_key=",
            "--multipart-chunk-size-mb=75",
            "put"
        ]
        for subFile in subFiles:
            s3UploadCmd.append(subFile + ".lzo")
        s3UploadCmd.append("s3://redfin.data-dumps/interana/events/")
        
        print "Uploading " + " ".join(s3UploadCmd)
        subprocess.call(s3UploadCmd)

if __name__ == "__main__":
    splitAndCompress(sys.argv[1:])
