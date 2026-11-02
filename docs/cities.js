// =====================================================================
//  CIUDADES Y CÁMARAS — edita este archivo en GitHub y la TV se actualiza sola.
//  cams: IDs de videos en vivo de YouTube (lo que va después de "watch?v=").
//  La primera es la principal; si falla, el launcher prueba la siguiente.
//  Solo cámaras revisadas SIN anuncios y SIN marca de agua (cada canal pone anuncios siempre o nunca).
//  kw: palabras que debe tener el título de una cámara encontrada automáticamente.
//  q: búsquedas que usa la TV para encontrar cámaras nuevas EN VIVO cuando todas las de la lista fallan.
// =====================================================================
window.CITIES = [
  { id:'tokyo', name:'Tokio', spot:'Shinjuku', flag:'🇯🇵', tz:'Asia/Tokyo', lat:35.659, lon:139.7, kw:["tokyo", "shibuya", "shinjuku", "東京", "渋谷", "新宿"], q:["shibuya scramble crossing live camera", "tokyo live camera 24/7"], cams:['6dp-bvQ7RWo'] },
  { id:'ny', name:'Nueva York', spot:'Times Square', flag:'🇺🇸', tz:'America/New_York', lat:40.758, lon:-73.985, kw:["new york", "nyc", "times square", "manhattan", "brooklyn"], q:["times square live cam", "new york city live cam 24/7"], cams:['mkX71WMAmNI','obqnslJKrxc','pv-2pVViNwc'] },
  { id:'niagara', name:'Cataratas del Niágara', spot:'Horseshoe Falls', flag:'🇨🇦', tz:'America/Toronto', lat:43.079, lon:-79.075, kw:["niagara"], q:["niagara falls live cam"], cams:['7gBzLGlJnwk'] },
  { id:'miami', name:'Miami', spot:'Hollywood Beach', flag:'🇺🇸', tz:'America/New_York', lat:25.79, lon:-80.13, kw:["miami"], q:["miami beach live cam", "miami skyline live cam"], cams:['cmkAbDUEoyA'] },
  { id:'la', name:'Los Ángeles', spot:'Muelle de Santa Mónica', flag:'🇺🇸', tz:'America/Los_Angeles', lat:34.008, lon:-118.499, kw:["los angeles", "venice beach", "santa monica"], q:["santa monica pier live cam", "venice beach live cam"], cams:['v97JpT3ZA0w'] },
  { id:'hawaii', name:'Honolulu', spot:'Waikiki', flag:'🇺🇸', tz:'Pacific/Honolulu', lat:21.276, lon:-157.827, kw:["waikiki", "honolulu", "hawaii"], q:["waikiki live cam", "honolulu live webcam"], cams:['pv2RBs3PwCs','f_yY-0miTzo'] },
  { id:'cancun', name:'Cancún', spot:'Playa', flag:'🇲🇽', tz:'America/Cancun', lat:21.11, lon:-86.76, kw:["cancun"], q:["cancun live cam beach", "royal resorts live webcam"], cams:['5D8BK_MLd-I','fQJRrJkDKo8','fv0Qlo_IofY','v2YavymJoK4'] },
  { id:'london', name:'Londres', spot:'London Bridge', flag:'🇬🇧', tz:'Europe/London', lat:51.532, lon:-0.178, kw:["london", "abbey road", "tower bridge"], q:["abbey road live earthcam", "london live webcam 24/7"], cams:['MXmzsjABC-U'] },
  { id:'paris', name:'París', spot:'Torre Eiffel', flag:'🇫🇷', tz:'Europe/Paris', lat:48.858, lon:2.294, kw:["paris", "eiffel"], q:["eiffel tower live webcam", "paris live webcam 24/7"], cams:['QI-PPEW2Bgc'] },
  { id:'ams', name:'Ámsterdam', spot:'Plaza Dam', flag:'🇳🇱', tz:'Europe/Amsterdam', lat:52.373, lon:4.893, kw:["amsterdam"], q:["amsterdam dam square live", "amsterdam live webcam"], cams:['Gd9d4q6WvUY'] },
  { id:'prague', name:'Praga', spot:'Centro', flag:'🇨🇿', tz:'Europe/Prague', lat:50.087, lon:14.421, kw:["prague", "praha", "praga"], q:["prague live webcam", "praha centrum živě"], cams:['3C8W4Rlgiog'] },
  { id:'venice', name:'Venecia', spot:'Gran Canal', flag:'🇮🇹', tz:'Europe/Rome', lat:45.437, lon:12.335, kw:["venice", "venezia"], q:["venice live webcam grand canal", "venezia live cam"], cams:['r8N6IsoIkTk'] },
  { id:'rome', name:'Roma', spot:'Cúpula de San Pedro', flag:'🇮🇹', tz:'Europe/Rome', lat:41.902, lon:12.457, kw:["rome", "roma", "vatican"], q:["rome live webcam", "st peter's dome live cam"], cams:['gXm3PGF4z0s','03pYP2Nmreo'] },
  { id:'jerusalem', name:'Jerusalén', spot:'Muro de los Lamentos', flag:'🇮🇱', tz:'Asia/Jerusalem', lat:31.776, lon:35.234, kw:["jerusalem", "western wall", "kotel"], q:["western wall live camera", "jerusalem live cam"], cams:['LMHUcDktP-w','AKGqd20ik_A','zp6LNSoq000'] },
  { id:'singapore', name:'Singapur', spot:'Centro', flag:'🇸🇬', tz:'Asia/Singapore', lat:1.283, lon:103.86, kw:["singapore"], q:["singapore live webcam", "singapore marina bay live cam"], cams:['rQ3VvxadJeo'] },
  { id:'hk', name:'Hong Kong', spot:'Bahía Victoria', flag:'🇭🇰', tz:'Asia/Hong_Kong', lat:22.293, lon:114.17, kw:["hong kong", "hongkong", "香港", "victoria harbour"], q:["hong kong victoria harbour live", "hong kong live cam"], cams:['6YYmUo2TgJ0','XN-ArdUrqtc'] },
  { id:'sydney', name:'Sídney', spot:'Puente y bahía', flag:'🇦🇺', tz:'Australia/Sydney', lat:-33.857, lon:151.215, kw:["sydney"], q:["sydney harbour live cam", "sydney opera house live webcam"], cams:['6Xxb5uikewE'] },
];

// Apps del menú. pkgs: paquetes de Fire TV / Android TV (usa el primero que esté instalado).
window.APPS = [
  { name:'Stremio',     mark:'◆',  color:'#8A5CF5', pkgs:['com.stremio.one'] },
  { name:'Netflix',     mark:'N',  color:'#E50914', pkgs:['com.netflix.ninja'] },
  { name:'Disney+',     mark:'D+', color:'#3b6cff', pkgs:['com.disney.disneyplus'] },
  { name:'YouTube',     mark:'▶',  color:'#FF0000', pkgs:['com.amazon.firetv.youtube','com.google.android.youtube.tv'] },
  { name:'Apple TV',    mark:'tv', color:'#FFFFFF', pkgs:['com.apple.atve.amazon.appletv','com.apple.atve.androidtv.appletv'] },
  { name:'Spotify',     mark:'●',  color:'#1DB954', pkgs:['com.spotify.tv.android'] },
  { name:'Prime Video', mark:'P',  color:'#00A8E1', pkgs:['com.amazon.firebat','com.amazon.avod','com.amazon.amazonvideo.livingroom'] },
];
