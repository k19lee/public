var React = require('react/addons'),
	fs = require("fs"),
	Busboy = require('busboy'),

	constants = require('../constants');
	
class UploadImageController {
	getPaths() {
		return {
			"/upload-image" : {
				get: this.render,
				post: this.handleUpload
			}
		};
	}

	render(req, res) {
		res.write(React.renderToString(
			<div>
				<form action="/upload-image" method="post" encType="multipart/form-data">
					<h1>Upload Image</h1>
					<input type="file" name="filefield" />
					<input type="submit" />
				</form>
			</div>
		));
	}
	
	handleUpload(req, res) {
		res.write("Please wait for the upload to complete...")
		
		var busboy = new Busboy({ headers: req.headers });
		busboy.on('file', function(fieldname, file, filename, encoding, mimetype) {
			if (filename === '') {
				file.resume();
				return;
			}
			
			// Enforce a max filesize here?
			file.pipe(fs.createWriteStream(constants.imagesDir + '/' + filename));
		});
		busboy.on('finish', function() {
			res.end(" Success!");
		});
		return req.pipe(busboy);
		
		return true;
	}
}

module.exports = UploadImageController;
