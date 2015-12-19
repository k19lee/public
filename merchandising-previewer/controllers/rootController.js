var React = require('react/addons');

class RootController {
	getPaths() {
		return {
			"/" : {
				get: this.run
			}
		};
	}
	
	run(req, res) {
		// Homepage has no content
	}
}

module.exports = RootController;
