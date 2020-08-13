function(doc) {
  var prep = {
    time : doc.time,
    stamp : doc.stamp,
    source : doc.source,
    target : doc.target,
    class : doc.class,
    sic : doc.sic,
    tap : doc.tap
  };
  if (doc.class=="strobe") {
    prep.angle = doc.angle;
  } else if (doc.class=="track") {
    prep.angle = doc.angle;
    prep.range = doc.range;
    prep.lat = doc.lat;
    prep.lon = doc.lon;
    prep.glyph = doc.glyph;
  } else if (doc.class=="plot") {
    prep.angle = doc.angle;
    prep.range = doc.range;
    prep.lat = doc.lat;
    prep.lon = doc.lon;
    prep.glyph = doc.glyph;
    prep.power = doc.power;
  }
  emit( [doc.stamp, doc.source], prep );
}