var React = require('react/addons'),

	constants = require('../constants'),
	Nav = require('../components/nav'),
	
	CommandLineHelper = require('../utils/commandLineHelper'),
	ScrollingTextArea = require('../utils/scrollingTextArea');

class PushToMasterController {
	getPaths() {
		return {
			"/push-to-master" : {
				get: this.render,
				post: this.handleSubmit
			}
		};
	}

	constructor() {
		this.textArea = new ScrollingTextArea('0');
	}
	
	getHtml(conf) {
		return React.renderToString(
			<div>
				<form action="/push-to-master" method="post">
					<div>
						<h2>Enter a description for these changes</h2>
						<input type="text" name="desc" size="100" />
					</div>
					<input type="submit" />
				</form>
			</div>
		);
	}
	
	render(req, res) {
		res.write(React.renderToString(
			<div>
				<h1>Push to Master</h1>
				
				Changes:
			</div>
		));
		
		res.write(this.textArea.open());
		
		var cmd = new CommandLineHelper();
		cmd.schedule(cmd.spawnCmd(res, 'eg', ['status'], {cwd: constants.corvairDir},
			function() {
				this.fail(res, 'Git failed');
			}.bind(this)));
		cmd.schedule(cmd.spawnCmd(res, 'eg', ['diff'], {cwd: constants.corvairDir},
			function() {
				this.fail(res, 'Git failed');
			}.bind(this)));
		cmd.schedule(cmd.functionCmd(function() {
			res.write(this.textArea.close());
			res.write(this.getHtml());
			res.end();
		}.bind(this)));
		cmd.run();
		
		return true;
	}
	
	fail(res, errorMessage) {
		res.write(this.textArea.close());
		res.write('<h1>' + errorMessage + '. Please try again</h1>');
		res.end();
	}
	
	handleSubmit(req, res) {
		var desc = req.body.desc;
		res.write(React.renderToString(
			<div>
				Running scripts... Please wait and do not leave this page.
			</div>
		));
		
		res.write(this.textArea.open());
		
		var cmd = new CommandLineHelper();
	    cmd.schedule(cmd.spawnCmd(res, 'eg', ['add', '-A'], {cwd: constants.corvairDir},
		    function() {
			    this.fail(res, 'Git failed');
		    }.bind(this)));
		cmd.schedule(cmd.spawnCmd(res, 'eg', ['commit', '-m', desc], {cwd: constants.corvairDir},
			function() {
				this.fail(res, 'Git failed');
			}.bind(this)));
		cmd.schedule(cmd.spawnCmd(res, 'eg', ['push'], {cwd: constants.corvairDir},
			function() {
				this.fail(res, 'Git failed');
			}.bind(this)));
		cmd.schedule(cmd.functionCmd(function() {
			res.write(this.textArea.close());
			res.write('<h1>Success! A build has been submitted, and when that succeeds, the changes will get merged into master</h1>');
			res.end();
		}.bind(this)));
		cmd.run();
		
		return true;
	}
	
}

module.exports = PushToMasterController;
