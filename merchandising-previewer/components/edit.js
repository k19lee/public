var React = require('react');

module.exports = React.createClass({
	render: function() {
		return(
			<div>
				<h1>Config File Editor</h1>
				<form action="/edit" method="post">
					<textarea name="config" rows="40" cols="200" defaultValue={this.props.conf} />
					<br />
					<br />
					<input type="checkbox" name="restartCorvair" value="true" id="restart_corvair" defaultChecked="true" />
					<label htmlFor="restart_corvair">Restart corvair if tests pass</label>
					<br/>
					<input type="submit" />
				</form>
			</div>
		);
	}
});
