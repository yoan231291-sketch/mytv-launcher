// =====================================================================
//  CIUDADES Y CÁMARAS — edita este archivo en GitHub y la TV se actualiza sola.
//  cams: IDs de videos en vivo de YouTube (lo que va después de "watch?v=").
//  La primera es la principal; si falla, el launcher prueba la siguiente.
// =====================================================================
window.CITIES = [
  { id:'tokyo',  name:'Tokio',            spot:'Cruce de Shibuya',   flag:'🇯🇵', tz:'Asia/Tokyo',          lat:35.659,  lon:139.700,  cams:['dfVK7ld38Ys','8H3nRCFVR6Y'] },
  { id:'ny',     name:'Nueva York',       spot:'Times Square',       flag:'🇺🇸', tz:'America/New_York',    lat:40.758,  lon:-73.985,  cams:['pv-2pVViNwc','VGnFLdQW39A','5C9oM7C2Q9k','VXo0VqVcbK4'] },
  { id:'paris',  name:'París',            spot:'Torre Eiffel',       flag:'🇫🇷', tz:'Europe/Paris',        lat:48.858,  lon:2.294,    cams:['QI-PPEW2Bgc','-xzg3wujOVM'] },
  { id:'london', name:'Londres',          spot:'London Bridge',      flag:'🇬🇧', tz:'Europe/London',       lat:51.507,  lon:-0.087,   cams:['znmUEZUM9Zc','zMCea32gpmg'] },
  { id:'venice', name:'Venecia',          spot:'Gran Canal',         flag:'🇮🇹', tz:'Europe/Rome',         lat:45.437,  lon:12.335,   cams:['r8N6IsoIkTk','aaJT8y3zfWs','7-XBRpKSv4Q'] },
  { id:'rome',   name:'Roma',             spot:'Vaticano',           flag:'🇻🇦', tz:'Europe/Rome',         lat:41.902,  lon:12.457,   cams:['89d3tEaqImM'] },
  { id:'dubai',  name:'Dubái',            spot:'Burj Khalifa',       flag:'🇦🇪', tz:'Asia/Dubai',          lat:25.197,  lon:55.274,   cams:['yzkwO8PsMAg','gK-I3UAlJYk'] },
  { id:'vegas',  name:'Las Vegas',        spot:'Bellagio / Fremont', flag:'🇺🇸', tz:'America/Los_Angeles', lat:36.113,  lon:-115.176, cams:['KJuGU7v5oYw','ZBVbqtk05d4'] },
  { id:'miami',  name:'Miami Beach',      spot:'South Beach',        flag:'🇺🇸', tz:'America/New_York',    lat:25.790,  lon:-80.130,  cams:['rItpj4wmabM','bi7B4EmyHHs'] },
  { id:'cdmx',   name:'Ciudad de México', spot:'Zócalo',             flag:'🇲🇽', tz:'America/Mexico_City', lat:19.432,  lon:-99.133,  cams:['oOsoFwMPbOg','gj6lXqFzzbQ'] },
  { id:'rio',    name:'Río de Janeiro',   spot:'Copacabana',         flag:'🇧🇷', tz:'America/Sao_Paulo',   lat:-22.985, lon:-43.189,  cams:['wyEi0OPai5g','Bz9Hkrk1NcY'] },
  { id:'hawaii', name:'Honolulu',         spot:'Waikiki',            flag:'🇺🇸', tz:'Pacific/Honolulu',    lat:21.276,  lon:-157.827, cams:['4CK0CsimBHs','pv2RBs3PwCs'] },
  { id:'ams',    name:'Ámsterdam',        spot:'Plaza Dam',          flag:'🇳🇱', tz:'Europe/Amsterdam',    lat:52.373,  lon:4.893,    cams:['Gd9d4q6WvUY','2tgHBRFHMm8'] },
  { id:'bcn',    name:'Barcelona',        spot:'Skyline',            flag:'🇪🇸', tz:'Europe/Madrid',       lat:41.403,  lon:2.174,    cams:['d1Hqy8eNi9w'] },
  { id:'seoul',  name:'Seúl',             spot:'Río Han',            flag:'🇰🇷', tz:'Asia/Seoul',          lat:37.528,  lon:126.932,  cams:['RVvYQghFCXk','vk5BHoDxXf0'] },
  { id:'sydney', name:'Sídney',           spot:'Bahía y Ópera',      flag:'🇦🇺', tz:'Australia/Sydney',    lat:-33.857, lon:151.215,  cams:['U2UfQH4lHAY','5uZa3-RMFos'] },
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
