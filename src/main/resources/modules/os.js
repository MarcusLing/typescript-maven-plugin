exports.EOL = (function() {
    return java.lang.System.getProperty("line.separator");
})();
exports.platform = function() {
  return "win64"; // FIXME
};

