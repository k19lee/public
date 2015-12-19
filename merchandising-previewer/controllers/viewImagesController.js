var React = require('react/addons'),
	fs = require("fs"),
	execSync = require('child_process').execSync,
	
	constants = require('../constants');

class ViewImagesController {
	getPaths() {
		return {
			"/view-images" : {
				get: this.run,
				post: this.run
			}
		};
	}

	run(req, res) {
		if (req.body.delete) {
			var file = req.body.delete.substring(req.body.delete.indexOf(' ') + 1);
			try {
				fs.unlinkSync(constants.imagesDir + '/' + file);
			} catch(e){}
		}
		if (req.body.undelete) {
			var file = req.body.undelete.substring(req.body.undelete.indexOf(' ') + 1);
			try {
				execSync("git checkout -- ../redfin.stingrayStatic/src/main/resources/images/homepage/banners/" + file,
						{cwd: constants.corvairDir});
			} catch(e){}
		}
		
		var cmd = "git status | grep deleted | grep redfin.stingrayStatic/src/main/resources/images/homepage/banners/ | awk '{print $2}'";
		var deletedImages = execSync(cmd, {cwd: constants.corvairDir, encoding: 'utf8'}).trim().split('\n');
		var deletedImageComponents = deletedImages.map((path) => {
			if (path == '') {
				return;
			}
			var image = path.substring(path.lastIndexOf('/') + 1);
			return <li key={image}><input type='submit' name="undelete" value={"Undelete " + image} /></li>;
		});
		
		var images = fs.readdirSync(constants.imagesDir);
		var imageComponents = images.map((image) => {
			return <div style={{padding: '5px', display: 'inline-block', border: 'solid', textAlign: 'center'}} key={image}>
				<a href={constants.localWebsiteRoot + '/images/homepage/banners/' + image}>
					<img src={constants.localWebsiteRoot + '/images/homepage/banners/' + image} width="300" />
				</a>
				<br />
				{image}
				<br />
				<form action="/view-images" method="post" style={{marginBottom:"0px"}}>
					<input type="hidden" name="delete" value={image} />
					<input type="submit" value="DELETE" />
				</form>
			</div>
		});
		res.write(React.renderToString(
			<div>
				<h1>Image Viewer</h1>
				<form action="/view-images" method="post">
					<ul>
						{deletedImageComponents}
					</ul>
				</form>
				{imageComponents}
			</div>
		));
	}
};
module.exports = ViewImagesController;
