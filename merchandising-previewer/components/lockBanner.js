var React = require('react');

module.exports = React.createClass({
	getDefaultProps: function() {
		return {
			browserId: null,
			state: {},
			lockContents: null
		};
	},
	
	render: function() {
		return (
			<div style={{width: "100%", textAlign: "center", padding: "2px", backgroundColor: "lightgray"}}>
				{this.getHeader()}
				{this.getCreated()}
				{this.getLastActivity()}
				{this.getChanges()}
				{this.getTakeLock()}
				{this.getReleaseLock()}
			</div>);
	},
	
	getHeader: function() {
		if ( this.props.lockContents == null ) {
			return <span>Nobody has the lock.</span>;
		} else if ( !this.props.state.hasLock ) {
			return<span>Locked by: <b>{this.props.lockContents.name}</b></span>;
		} else {
			return<span>You are holding the lock</span>;
		}
	},
	
	getCreated: function() {
		if ( this.props.lockContents != null ) {
			return <div>Created {this.getTimeAgo(this.props.lockContents.created)}</div>;
		}
	},
	
	getLastActivity: function() {
		if ( !this.props.state.hasLock && this.props.lockContents != null ) {
			return <div>Last Activity {this.getTimeAgo(this.props.lockContents.lastActivity)}</div>;
		}
	},
	
	getChanges: function() {
		if ( this.props.state.modified ) { 
			return <form method="post" style={{marginBottom: "0px"}}>
				There are uncommitted changes
				{ this.props.state.hasLock ? 
					<input type='submit' name='revert' value='Revert Changes' />
					: ""
				}
			</form>;
		}
	},
	
	getTakeLock: function() {
		if ( !this.props.state.hasLock ) {
			if ( this.props.browserId ) {
				return (
					<form method="post">
						Enter your name to take the lock:
						<input type='text' name='name' />
						<input type='submit' name='acquire' value='Take Lock' />
					</form>);
			} else {
				return (
					<div>
						You can't take the lock without a browserId.
						Visit <a href="https://trunk.redfintest.com">Trunk Redfintest</a> then come back
					</div>);
			}
		}
	},
	
	getReleaseLock: function() {
		if ( this.props.state.hasLock ) {
			return (
				<form method="post">
					Release your lock when you're done
					<input type='submit' name='release' value='Release Lock' />
				</form>);
		}
	},
	
	getTimeAgo: function(pastTime) {
		var diff = new Date().getTime() - pastTime;
		diff = Math.floor(diff / 1000);
		var seconds = diff % 60;
		diff = Math.floor(diff / 60);
		var minutes = diff % 60;
		diff = Math.floor(diff / 60);
		var hours = diff % 24;
		diff = Math.floor(diff / 24);
		var days = diff;
		
		var str = "";
		if ( days != 0 ) {
			str += days + " days ";
		}
		if ( hours != 0 ) {
			str += hours + " hours ";
		}
		if ( minutes != 0 ) {
			str += minutes + " minutes ";
		}
		str += seconds + " seconds ago";
		return str;
	}
});
