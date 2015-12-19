var constants = require('../constants'),
	fs = require("fs"),
	execSync = require('child_process').execSync;

class WriteLock {
	isMine(browserId, lockContents) {
		return lockContents != null && lockContents.browserId === browserId;
	}
	
	update(lockContents) {
		return this._writeLock(lockContents.browserId, lockContents.name, lockContents.created, new Date().getTime());
	}
	
	acquire(browserId, name) {
		return this._writeLock(browserId, name, new Date().getTime(), new Date().getTime());
	}
	
	_writeLock(new_browserId, new_name, new_created, new_lastActivity) {
		this.release();
		fs.writeFileSync(constants.lockFile, JSON.stringify({
			browserId: new_browserId,
			name: new_name,
			created: new_created,
			lastActivity: new_lastActivity
		}));
		
		// Read the result after writing to ensure the write worked.
		var lock = this.read();
		return lock.browserId === new_browserId;
	}
	
	read() {
		if ( !fs.existsSync(constants.lockFile ) ) {
			return null;
		} else {
			// Expect a JSON file
			return JSON.parse(fs.readFileSync(constants.lockFile));
		}
	}
	
	release() {
		if ( fs.existsSync(constants.lockFile) ) {
			fs.unlinkSync(constants.lockFile);
		}
	}
};

module.exports = new WriteLock();
