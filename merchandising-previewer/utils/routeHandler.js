var React = require('react/addons'),

	writeLock = require('./writeLock'),
	execSync = require('child_process').execSync,
	constants = require('../constants'),

	LockBanner = require('../components/lockBanner'),
	Nav = require('../components/nav');
	
class RouteHandler {
	constructor(app) {
		this.app = app;
	}
	
	add(method, path, controller) {
		this.app[method](path, (req, res) => {
			// Handle lock operations on every post
			if ( method == 'post' ) {
				if ( this.handleLockOperations(req, res) ) {
					// If we handled an operation, then redirect to the current path to execute the "get" version of the page
					res.writeHead(302, {'Location': path});
					res.end();
					return;
				}
			}
			
			var lockContents = writeLock.read();
			var state = {
				hasLock: writeLock.isMine(req.cookies.RF_BROWSER_ID, lockContents),
				modified: this.hasModifications()
			};
			if ( state.hasLock ) {
				writeLock.update(lockContents);
			}
			
			res.writeHead(200, {'Content-Type': 'text/html; charset=utf-8' });
			res.write(React.renderToString(
				<div>
					<LockBanner browserId={req.cookies.RF_BROWSER_ID} lockContents={lockContents} state={state} />
					<Nav />
					{this.errorMsg}
				</div>
			));
			this.errorMsg = "";
			
			let isStreaming = false;
			
			if ( method == 'post' && !state.hasLock ) {
				res.write("Operation not permitted. You do not have the lock");
			} else {
				// If the handler method returns true, then don't close the response
				isStreaming = controller(req, res, state);
			}
			
			if ( !isStreaming ) {
				res.end();
			}
		});
	}
	
	handleLockOperations(req, res) {
		// Acquire the lock
		if ( req.body.name && req.body.acquire ) {
			var success = writeLock.acquire(req.cookies.RF_BROWSER_ID, req.body.name);
			if ( !success ) {
				this.errorMsg = "Failed to acquire lock! Please try again";
				return false;
			} else {
				return true;
			}
		} else if ( req.body.release ) {
			var lockContents = writeLock.read();
			if ( writeLock.isMine(req.cookies.RF_BROWSER_ID, lockContents) ) {
				writeLock.release();
				return true;
			} else {
				return false;
			}
		} else if ( req.body.revert ) {
			var lockContents = writeLock.read();
			if ( writeLock.isMine(req.cookies.RF_BROWSER_ID, lockContents) ) {
				this.revertChanges(req);
				return true;
			} else {
				return false;
			}
		}
		return false;
	}
	
	revertChanges(req) {
		execSync("eg revert '" + constants.mfConfigFile + "'", {cwd: constants.corvairDir});
		// Restore deleted images
		execSync("eg revert ../redfin.stingrayStatic/src/main/resources/images/homepage/banners", {cwd: constants.corvairDir});
		// Delete new files
		execSync("git clean -f ../", {cwd: constants.corvairDir});
	}
	
	hasModifications() {
		var isModified = false;
		try {
			isModified = execSync("git ls-files -m -o pages/merch/helpers/MerchConfig.js ../redfin.stingrayStatic/src/main/resources/images/homepage/banners | grep '^'",
									{cwd: constants.corvairDir});
			// This either returns "false" if there's no output, or a Buffer of the stdout
			if ( isModified !== false ) {
				isModified = true;
			}
		} catch(e){}
		return isModified;
	}
};

module.exports = RouteHandler;
