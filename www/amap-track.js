var exec = require('cordova/exec');

// 这里的命令AMapTrackPlugin与plugin.xml无关，用abc都可以，module.exports用于require('')
function AMapTrackPlugin() {}

AMapTrackPlugin.prototype.startTrack = function(successCallback, errorCallback, options) {
	exec(successCallback, errorCallback, "AMapTrackPlugin", "startTrack", [
		options.serviceId,
		options.terminalName,
		options.trackId,
		options.locationInterval,
		options.uploadInterval,
		options.uploadToTrack
	]);
};

AMapTrackPlugin.prototype.stopTrack = function(successCallback, errorCallback) {
	exec(successCallback, errorCallback, "AMapTrackPlugin", "stopTrack", []);
};

AMapTrackPlugin.prototype.startGather = function(successCallback, errorCallback, options) {
	exec(successCallback, errorCallback, "AMapTrackPlugin", "startGather", [
		options.trackId
	]);
};

AMapTrackPlugin.prototype.stopGather = function(successCallback, errorCallback) {
	exec(successCallback, errorCallback, "AMapTrackPlugin", "stopGather", []);
};

module.exports = new AMapTrackPlugin();
