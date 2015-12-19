var spawn = require('child_process').spawn,
	Encoder = require('node-html-encoder').Encoder;

class CommandLineHelper {
	constructor() {
		this.commands = [];
		this.runIndex = 0;
	}
	
	schedule(cmd) {
		this.commands.push(cmd);
	}
	
	spawnCmd(res, command, args, options, onFail, failRegex) {
		return () => {
			var cmd = spawn(command, args, options);
			var encoder = new Encoder('entity');
			var onDataFunc = (data) => {
				res.write(encoder.htmlEncode(data.toString()));
				if ( failRegex != null && data.toString().search(failRegex) != -1 ) {
					cmd.kill(); // This will force a non-zero exit code, which should call onFail below
					return;
				}
			};
			if ( options.detached ) {
				// detached processes like corvair should be fire and forget
				cmd.unref();
				this.runIndex++;
				this.run();
			} else {
				cmd.stdout.on('data', onDataFunc);
				cmd.stderr.on('data', onDataFunc);
				cmd.on('close', function(exitCode) {
					if (exitCode != 0) {
						onFail();
						return;
					}
					
					this.runIndex++;
					this.run();
				}.bind(this));
			}
		}
	}
	
	functionCmd(func) {
		return () => {
			func();
			
			this.runIndex++;
			this.run();
		}
	}
	
	run() {
		if ( this.runIndex < this.commands.length ) {
			this.commands[this.runIndex]();
		}
	}
}

module.exports = CommandLineHelper;
