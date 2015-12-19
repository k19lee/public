var React = require('react/addons'),
	fs = require("fs"),
	exec = require('child_process').exec,
	
	constants = require('../constants'),
	Edit = require('../components/edit'),

	CommandLineHelper = require('../utils/commandLineHelper'),
	ScrollingTextArea = require('../utils/scrollingTextArea');
	
class EditController {
	getPaths() {
		return {
			"/edit" : {
				get: this.render,
				post: this.handleEdit
			}
		};
	}
	
	constructor() {
		this.textArea = new ScrollingTextArea('0');
	}
	
	render(req, res, state) {
		var conf = fs.readFileSync(constants.mfConfigFile, "utf8");
		
		res.write(React.renderToString(
				<Edit conf={conf} />
		));
	}
	
	fail(res, errorMessage) {
		fs.writeFileSync(constants.mfConfigFile, this.oldConf);
		res.write(this.textArea.close());
		res.write('<h1>' + errorMessage + ' Please try again below:</h1>');
		res.write(React.renderToString(<Edit conf={this.newConf} />));
		res.end();
	}
	
	handleEdit(req, res) {
		var restartCorvair = req.body.restartCorvair;
		this.oldConf = fs.readFileSync(constants.mfConfigFile, "utf8");
		this.newConf = req.body.config.replace(/\r\n/g, "\n");
		fs.writeFileSync(constants.mfConfigFile, this.newConf);
		
		res.write(React.renderToString(
			<div>
				Running validators... Please wait and do not leave this page.
			</div>
		));
		
		res.write(this.textArea.open());
		
		var cmd = new CommandLineHelper();
		cmd.schedule(cmd.spawnCmd(res, './corvair', ['-l'], {cwd: constants.corvairDir},
			function() {
				this.fail(res, 'Code style checks failed!!!');
			}.bind(this),
			/'eslint' errored/
			));
		cmd.schedule(cmd.spawnCmd(res, './corvair', ['--test', '--specs', 'MerchConfigSpec.js'], {cwd: constants.corvairDir},
			function() {
				this.fail(res, 'Config validation failed!!!');
			}.bind(this),
			/An error occurred running tests/));
		if ( restartCorvair ) {
			if ( !fs.existsSync(constants.logsDir ) ) {
				fs.mkdirSync(constants.logsDir);
			}
			var corvairOut = constants.logsDir + "/corvair.out";
			var corvairErr = constants.logsDir + "/corvair.err";
			if ( fs.existsSync(corvairOut) ) {
				fs.unlinkSync(corvairOut);
			}
			if ( fs.existsSync(corvairErr) ) {
				fs.unlinkSync(corvairErr);
			}
			
			cmd.schedule(cmd.functionCmd(() => {
				res.write("Restarting Corvair... It will be up in 30 seconds or so...");
				exec("pkill -f target/corvair/start.js");
			}));
			
			cmd.schedule(cmd.spawnCmd(res, "./corvair", ['-bs'], {
					cwd: constants.corvairDir,
					detached: true,
					stdio: ['ignore', fs.openSync(corvairOut, 'w'), fs.openSync(corvairErr, 'w')]
				}));
		}
		cmd.schedule(cmd.functionCmd(() => {
			res.write(this.textArea.close());
			res.write('<h1>Success!</h1>');
			res.end();
		}));
		cmd.run();
		
		return true;
	}
}

module.exports = EditController;
