+String {
	urlDecode {
		var str = this;

		// @TODO More url encode replacements...
		var replacements = (
			"%20": " "
		);

		replacements.keysValuesDo {
			|key, value|
			str = str.replace(key, value)
		};

		^str
	}
}