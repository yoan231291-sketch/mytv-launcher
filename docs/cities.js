// =====================================================================
//  CIUDADES Y CÁMARAS — edita este archivo en GitHub y la TV se actualiza sola.
//  cams: IDs de videos en vivo de YouTube (lo que va después de "watch?v=").
//  La primera es la principal; si falla, el launcher prueba la siguiente.
//  kw: palabras que debe tener el título de una cámara encontrada automáticamente.
//  q: búsquedas que usa la TV para encontrar cámaras nuevas EN VIVO cuando todas las de la lista fallan.
// =====================================================================
window.CITIES = [
  { id:'tokyo', name:'Tokio', spot:'Cruce de Shibuya', flag:'🇯🇵', tz:'Asia/Tokyo', lat:35.659, lon:139.7, kw:["tokyo", "shibuya", "shinjuku", "東京", "渋谷", "新宿"], q:["shibuya scramble crossing live camera", "tokyo live camera 24/7"], cams:['dfVK7ld38Ys','6dp-bvQ7RWo'] },
  { id:'ny', name:'Nueva York', spot:'Times Square', flag:'🇺🇸', tz:'America/New_York', lat:40.758, lon:-73.985, kw:["new york", "nyc", "times square", "manhattan", "brooklyn"], q:["times square live cam", "new york city live cam 24/7"], cams:['mkX71WMAmNI','obqnslJKrxc','pv-2pVViNwc'] },
  { id:'chicago', name:'Chicago', spot:'Skyline', flag:'🇺🇸', tz:'America/Chicago', lat:41.879, lon:-87.636, kw:["chicago"], q:["chicago live cam", "chicago skyline live webcam"], cams:['O0UGT7AT3aw'] },
  { id:'niagara', name:'Cataratas del Niágara', spot:'Horseshoe Falls', flag:'🇨🇦', tz:'America/Toronto', lat:43.079, lon:-79.075, kw:["niagara"], q:["niagara falls live cam"], cams:['7gBzLGlJnwk','qx7gry390YA'] },
  { id:'miami', name:'Miami', spot:'Bahía y playa', flag:'🇺🇸', tz:'America/New_York', lat:25.79, lon:-80.13, kw:["miami"], q:["miami beach live cam", "miami skyline live cam"], cams:['GkBMgHwSIV0','bi7B4EmyHHs','H91O7OQudcc'] },
  { id:'vegas', name:'Las Vegas', spot:'Bellagio', flag:'🇺🇸', tz:'America/Los_Angeles', lat:36.113, lon:-115.176, kw:["las vegas", "vegas", "fremont", "bellagio"], q:["las vegas strip live cam", "fremont street live cam"], cams:['KJuGU7v5oYw','uaIhqF1hz0I'] },
  { id:'sf', name:'San Francisco', spot:'Bahía y Bay Bridge', flag:'🇺🇸', tz:'America/Los_Angeles', lat:37.799, lon:-122.398, kw:["san francisco", "golden gate", "bay bridge"], q:["san francisco live webcam", "golden gate bridge live cam"], cams:['CXYr04BWvmc','G8RIAgPxaMc','BSWhGNXxT9A'] },
  { id:'la', name:'Los Ángeles', spot:'Santa Mónica y Venice', flag:'🇺🇸', tz:'America/Los_Angeles', lat:34.008, lon:-118.499, kw:["los angeles", "venice beach", "santa monica"], q:["santa monica pier live cam", "venice beach live cam"], cams:['v97JpT3ZA0w','0IgjqQzibqE'] },
  { id:'hawaii', name:'Honolulu', spot:'Waikiki', flag:'🇺🇸', tz:'Pacific/Honolulu', lat:21.276, lon:-157.827, kw:["waikiki", "honolulu", "hawaii"], q:["waikiki live cam", "honolulu live webcam"], cams:['kCPppYpcY_s','pv2RBs3PwCs','f_yY-0miTzo'] },
  { id:'cdmx', name:'Ciudad de México', spot:'Plaza Garibaldi', flag:'🇲🇽', tz:'America/Mexico_City', lat:19.44, lon:-99.139, kw:["ciudad de mexico", "mexico city", "cdmx", "zocalo", "garibaldi", "reforma", "bellas artes"], q:["plaza garibaldi en vivo", "ciudad de mexico camara en vivo"], cams:['KBU8GXi-XMI'] },
  { id:'cancun', name:'Cancún', spot:'Playa', flag:'🇲🇽', tz:'America/Cancun', lat:21.11, lon:-86.76, kw:["cancun"], q:["cancun live cam beach", "royal resorts live webcam"], cams:['5D8BK_MLd-I','fQJRrJkDKo8','fv0Qlo_IofY','v2YavymJoK4'] },
  { id:'rio', name:'Río de Janeiro', spot:'Copacabana', flag:'🇧🇷', tz:'America/Sao_Paulo', lat:-22.985, lon:-43.189, kw:["rio de janeiro", "copacabana", "ipanema"], q:["copacabana live cam", "rio de janeiro webcam ao vivo"], cams:['wyEi0OPai5g','Bz9Hkrk1NcY','WEl_I5vmuSs'] },
  { id:'london', name:'Londres', spot:'Abbey Road', flag:'🇬🇧', tz:'Europe/London', lat:51.532, lon:-0.178, kw:["london", "abbey road", "tower bridge"], q:["abbey road live earthcam", "london live webcam 24/7"], cams:['zMCea32gpmg','MXmzsjABC-U'] },
  { id:'dublin', name:'Dublín', spot:'Temple Bar', flag:'🇮🇪', tz:'Europe/Dublin', lat:53.345, lon:-6.264, kw:["dublin"], q:["earthcam dublin live", "dublin live webcam"], cams:['3nyPER2kzqk'] },
  { id:'paris', name:'París', spot:'Torre Eiffel', flag:'🇫🇷', tz:'Europe/Paris', lat:48.858, lon:2.294, kw:["paris", "eiffel"], q:["eiffel tower live webcam", "paris live webcam 24/7"], cams:['QI-PPEW2Bgc'] },
  { id:'ams', name:'Ámsterdam', spot:'Plaza Dam', flag:'🇳🇱', tz:'Europe/Amsterdam', lat:52.373, lon:4.893, kw:["amsterdam"], q:["amsterdam dam square live", "amsterdam live webcam"], cams:['Gd9d4q6WvUY'] },
  { id:'prague', name:'Praga', spot:'Centro', flag:'🇨🇿', tz:'Europe/Prague', lat:50.087, lon:14.421, kw:["prague", "praha", "praga"], q:["prague live webcam", "praha centrum živě"], cams:['3C8W4Rlgiog'] },
  { id:'venice', name:'Venecia', spot:'Gran Canal', flag:'🇮🇹', tz:'Europe/Rome', lat:45.437, lon:12.335, kw:["venice", "venezia"], q:["venice live webcam grand canal", "venezia live cam"], cams:['7-XBRpKSv4Q','r8N6IsoIkTk','aaJT8y3zfWs','gzgrofStPJ0'] },
  { id:'rome', name:'Roma', spot:'Cúpula de San Pedro', flag:'🇮🇹', tz:'Europe/Rome', lat:41.902, lon:12.457, kw:["rome", "roma", "vatican"], q:["rome live webcam", "st peter's dome live cam"], cams:['gXm3PGF4z0s','03pYP2Nmreo'] },
  { id:'bcn', name:'Barcelona', spot:'Skyline', flag:'🇪🇸', tz:'Europe/Madrid', lat:41.403, lon:2.174, kw:["barcelona"], q:["barcelona live webcam", "barcelona en directo camara"], cams:['d1Hqy8eNi9w'] },
  { id:'jerusalem', name:'Jerusalén', spot:'Muro de los Lamentos', flag:'🇮🇱', tz:'Asia/Jerusalem', lat:31.776, lon:35.234, kw:["jerusalem", "western wall", "kotel"], q:["western wall live camera", "jerusalem live cam"], cams:['LMHUcDktP-w','AKGqd20ik_A','zp6LNSoq000'] },
  { id:'bangkok', name:'Bangkok', spot:'Calles', flag:'🇹🇭', tz:'Asia/Bangkok', lat:13.75, lon:100.5, kw:["bangkok"], q:["bangkok live cam", "bangkok live webcam 24/7"], cams:['w8QCbgRo6FY'] },
  { id:'singapore', name:'Singapur', spot:'Centro', flag:'🇸🇬', tz:'Asia/Singapore', lat:1.283, lon:103.86, kw:["singapore"], q:["singapore live webcam", "singapore marina bay live cam"], cams:['rQ3VvxadJeo'] },
  { id:'hk', name:'Hong Kong', spot:'Bahía Victoria', flag:'🇭🇰', tz:'Asia/Hong_Kong', lat:22.293, lon:114.17, kw:["hong kong", "hongkong", "香港", "victoria harbour"], q:["hong kong victoria harbour live", "hong kong live cam"], cams:['6YYmUo2TgJ0','XN-ArdUrqtc'] },
  { id:'seoul', name:'Seúl', spot:'Torre Namsan y río Han', flag:'🇰🇷', tz:'Asia/Seoul', lat:37.528, lon:126.932, kw:["seoul", "서울", "han river", "hangang", "한강"], q:["seoul han river live cam", "seoul live camera 24/7"], cams:['GhNscofIqHU','5gVrWGRZo9s','RVvYQghFCXk'] },
  { id:'sydney', name:'Sídney', spot:'Puente y bahía', flag:'🇦🇺', tz:'Australia/Sydney', lat:-33.857, lon:151.215, kw:["sydney"], q:["sydney harbour live cam", "sydney opera house live webcam"], cams:['6Xxb5uikewE','5uZa3-RMFos'] },
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
