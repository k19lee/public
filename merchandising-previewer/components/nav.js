var React = require('react'),
	constants = require('../constants'),
	execSync = require('child_process').execSync;

module.exports = React.createClass({
	render: function() {
		var corvairIsRunning = this.findProcess("3333", "iojs");
		var stingrayIsRunning = this.findProcess("8888", "java");
		var commerceIsRunning = this.findProcess("8999", "java");
		
		return(
			<div>
				<div style={{float: "left"}}>
					<ul>
						<li><a href="/">Home</a></li>
						<li><a href="/edit">Edit Config</a></li>
						<li><a href="/view-images">View Images</a></li>
						<li><a href="/upload-image">Upload Image</a></li>
						<li><a href="/screenshots">View Device Screenshots</a></li>
						<li><a href="/push-to-master">Push to Master</a></li>
					</ul>
				</div>
				<div style={{float: "right", marginRight: "200px"}}>
					Server status:
					<ul>
						<li>{corvairIsRunning ? <span style={{color: "green"}}>Corvair is up</span> : <span style={{color: "red"}}>Corvair is down</span>}</li>
						<li>{stingrayIsRunning ? <span style={{color: "green"}}>Stingray is up</span> : <span style={{color: "red"}}>Stingray is down</span>}</li>
						<li>{commerceIsRunning ? <span style={{color: "green"}}>Commerce is up</span> : <span style={{color: "red"}}>Commerce is down</span>}</li>
					</ul>
				</div>
				<div style={{clear: "both"}}></div>
			</div>
		);
	},
	
	findProcess: function(port, regex) {
		var found = false;
		try {
			var status = execSync("lsof -i :" + port + " | grep -e '" + regex + "'", {cwd: constants.corvairDir});
			status.toString().split("\n").forEach(function(value, index) {
				if ( value.length > 0 ) {
					found = true;
				}
			});
		} catch(e) {}
		return found;
	}
});


