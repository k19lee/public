class ScrollingTextArea {
	constructor(id) {
		this.domId = id;
	}
	
	open() {
		return '<script>' +
			'function scrollDown(domId) { ' +
				'var e = document.getElementById(domId); ' +
				'e.scrollTop = e.scrollHeight; ' +
			'}; ' +
			'intervalVal_' + this.domId + ' = setInterval(function() { scrollDown("cmdOutput_' + this.domId + '", 100 ); });' +
		'</script> ' +
		'<textarea rows="20" cols="200" id="cmdOutput_' + this.domId + '">';
	}
	
	close() {
		return '</textarea>' +
			'<script>' +
				'clearInterval(intervalVal_' + this.domId + ');' +
				'scrollDown("cmdOutput_' + this.domId + '");' +
			'</script>';
	}
}

module.exports = ScrollingTextArea;
