var React = require('react/addons'),
	fs = require("fs"),
	
	constants = require('../constants'),
	
	CommandLineHelper = require('../utils/commandLineHelper'),
	ScrollingTextArea = require('../utils/scrollingTextArea');

class ScreenshotController {
	getPaths() {
		return {
			"/screenshots" : {
				get: this.render,
				post: this.handleSubmit
			}
		};
	}

	constructor() {
		this.textArea = new ScrollingTextArea('0');
		
		try {
			if ( !fs.existsSync(constants.screenshotDir ) ) {
				fs.mkdirSync(constants.screenshotDir);
			}
		} catch(e) {
			console.log("Error creating screenshot directory: " + e);
		}
	}
	
	render(req, res) {
		if ( req.query.exp ) {
			this.showExperience(req, res, req.query.exp );
		} else {
			this.showExperienceList(req, res);
		}
	}
	
	showExperienceList(req, res) {
		var images = fs.readdirSync(constants.screenshotDir);
		
		// The files are in the form of <placement>.<experience>_<resolution>.png
		// For the experience list, group everything by <placement>.<experience>
		var experiences = [];
		for ( var i = 0; i < images.length; i++ ) {
			var lastUnderscore = images[i].lastIndexOf('_');
			var experienceName = images[i].substring(0, lastUnderscore);
			
			// readdir returns a sorted list, so we only have to check the previous element to know
			// if it's a new entry or not.
			if ( experiences.length == 0 || experiences[experiences.length-1] !== experienceName ) {
				experiences.push(experienceName);
			}
		}
		var experienceComponents = experiences.map((exp) => {
			return <li key={exp}><a href={"/screenshots?exp=" + exp}>{exp}</a></li>;
		});
		
		res.write(React.renderToString(
			<div>
				<h1>Screenshot Viewer</h1>
				Experiences:
				<ul>
					{experienceComponents}
				</ul>
				
				<hr />
				<h2>Recreate Screenshots</h2>
				<div>
					This will delete everything and regenerate screenshots for all the experiences in the current config.<br/>
					Takes about 5-10 minutes
					<form action="/screenshots" method="post">
						<input type="submit" value="Delete and Regenerate Screenshots" />
					</form>
				</div>
				
			</div>
		));
	}
	
	showExperience(req, res, exp) {
		var images = fs.readdirSync(constants.screenshotDir);
		
		images = images.filter((path) => {
			return path.indexOf(exp) === 0;
		});
		
		var imageComponents = images.map((image) => {
			var imageSuffix = image.substring(exp.length+1);
			
			return <div style={{margin: '5px', textAlign: 'center', display: 'inline-block'}} key={image}>
				<div>{imageSuffix}</div>
				<a href={constants.localWebsiteRoot + ':8888/' + image}>
					<div style={{border: 'solid', padding: '5px', margin: '0px auto',
								backgroundImage: 'url(\'' + constants.localWebsiteRoot + ':8888/' + image + '\')',
								backgroundSize: 'cover',
								width: '300px', height: '400px'
								}}>
					</div>
				</a>
			</div>
		});
		
		res.write(React.renderToString(
			<div>
				<h1>Screenshot Viewer</h1>
				<div>Experience: {exp}</div>
				<br/>
				{imageComponents}
			</div>
		));
	}
	
	fail(res, errorMessage) {
		res.write(this.textArea.close());
		res.write('<h1>' + errorMessage + '</h1>');
		res.end();
	}
	
	handleSubmit(req, res) {
		res.write(React.renderToString(
			<div>
				Running screenshot utility... Please wait and do not leave this page.
			</div>
		));
		
		res.write(this.textArea.open());
		
		try {
			fs.readdirSync(constants.screenshotDir).forEach(function(file, index) {
				fs.unlinkSync(constants.screenshotDir + "/" + file);
			});
		} catch (e) {
			this.fail(res, 'Error deleting screenshots:' + e);
			return;
		}
		
		var cmd = new CommandLineHelper();
		
		process.env['MERCH_CONFIG_PATH'] = constants.mfConfigFile;
		process.env['SCREENSHOT_OUTPUT_DIR'] = constants.screenshotDir;
		cmd.schedule(cmd.spawnCmd(res, 'mvn', ['test', '-Dmaven.test.skip.exec=false', '-DenvironmentString=dev_trunk', '-Dsuite=src/test/resources/suites/RampScreenshotter'], {
				cwd: constants.corvairDir + '/../redfin.qa/redfin.qa.tests'
			},
			() => {this.fail(res, "Screenshotter failed!");}));
		cmd.schedule(cmd.functionCmd(() => {
			res.write(this.textArea.close());
			res.write('<h1>Success!</h1>');
			res.end();
		}));
		cmd.run();
		
		return true;
	}
	
};

module.exports = ScreenshotController;
