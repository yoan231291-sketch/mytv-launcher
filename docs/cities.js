// =====================================================================
//  CIUDADES Y CÁMARAS — edita este archivo en GitHub y la TV se actualiza sola.
//  cams: IDs de videos en vivo de YouTube (lo que va después de "watch?v=").
//  La primera es la principal; si falla, el launcher prueba la siguiente.
//  q: búsquedas que usa la TV para encontrar cámaras nuevas EN VIVO cuando todas las de la lista fallan.
// =====================================================================
window.CITIES = [
  { id:'tokyo',  name:'Tokio',            spot:'Cruce de Shibuya',   flag:'🇯🇵', tz:'Asia/Tokyo',          lat:35.659,  lon:139.700,  q:["shibuya scramble crossing live camera","tokyo live camera 24/7"], cams:['dfVK7ld38Ys','8H3nRCFVR6Y'] },
  { id:'ny',     name:'Nueva York',       spot:'Times Square',       flag:'🇺🇸', tz:'America/New_York',    lat:40.758,  lon:-73.985,  q:["times square live cam","new york city live cam 24/7","earthcam new york live"], cams:['pv-2pVViNwc','VGnFLdQW39A','5C9oM7C2Q9k','VXo0VqVcbK4'] },
  { id:'paris',  name:'París',            spot:'Torre Eiffel',       flag:'🇫🇷', tz:'Europe/Paris',        lat:48.858,  lon:2.294,    q:["eiffel tower live webcam","paris live webcam 24/7"], cams:['QI-PPEW2Bgc','-xzg3wujOVM'] },
  { id:'london', name:'Londres',          spot:'London Bridge',      flag:'🇬🇧', tz:'Europe/London',       lat:51.507,  lon:-0.087,   q:["london live webcam 24/7","abbey road live earthcam","tower bridge live cam"], cams:['znmUEZUM9Zc','zMCea32gpmg'] },
  { id:'venice', name:'Venecia',          spot:'Gran Canal',         flag:'🇮🇹', tz:'Europe/Rome',         lat:45.437,  lon:12.335,   q:["venice live webcam grand canal","venezia live cam"], cams:['r8N6IsoIkTk','aaJT8y3zfWs','7-XBRpKSv4Q'] },
  { id:'rome',   name:'Roma',             spot:'Vaticano',           flag:'🇻🇦', tz:'Europe/Rome',         lat:41.902,  lon:12.457,   q:["rome live webcam","roma live cam vaticano","trevi fountain live cam"], cams:['89d3tEaqImM'] },
  { id:'dubai',  name:'Dubái',            spot:'Burj Khalifa',       flag:'🇦🇪', tz:'Asia/Dubai',          lat:25.197,  lon:55.274,   q:["dubai live cam burj khalifa","dubai live webcam 24/7"], cams:['yzkwO8PsMAg','gK-I3UAlJYk'] },
  { id:'vegas',  name:'Las Vegas',        spot:'Bellagio / Fremont', flag:'🇺🇸', tz:'America/Los_Angeles', lat:36.113,  lon:-115.176, q:["las vegas strip live cam","fremont street live cam","las vegas live webcam"], cams:['KJuGU7v5oYw','ZBVbqtk05d4'] },
  { id:'miami',  name:'Miami Beach',      spot:'South Beach',        flag:'🇺🇸', tz:'America/New_York',    lat:25.790,  lon:-80.130,  q:["miami beach live cam","south beach live cam","miami live webcam"], cams:['rItpj4wmabM','bi7B4EmyHHs'] },
  { id:'cdmx',   name:'Ciudad de México', spot:'Zócalo',             flag:'🇲🇽', tz:'America/Mexico_City', lat:19.432,  lon:-99.133,  q:["zocalo ciudad de mexico en vivo","ciudad de mexico camara en vivo"], cams:['oOsoFwMPbOg','gj6lXqFzzbQ'] },
  { id:'rio',    name:'Río de Janeiro',   spot:'Copacabana',         flag:'🇧🇷', tz:'America/Sao_Paulo',   lat:-22.985, lon:-43.189,  q:["copacabana live cam","rio de janeiro webcam ao vivo"], cams:['wyEi0OPai5g','Bz9Hkrk1NcY'] },
  { id:'hawaii', name:'Honolulu',         spot:'Waikiki',            flag:'🇺🇸', tz:'Pacific/Honolulu',    lat:21.276,  lon:-157.827, q:["waikiki live cam","honolulu live webcam"], cams:['4CK0CsimBHs','pv2RBs3PwCs'] },
  { id:'ams',    name:'Ámsterdam',        spot:'Plaza Dam',          flag:'🇳🇱', tz:'Europe/Amsterdam',    lat:52.373,  lon:4.893,    q:["amsterdam live webcam","amsterdam dam square live"], cams:['Gd9d4q6WvUY','2tgHBRFHMm8'] },
  { id:'bcn',    name:'Barcelona',        spot:'Skyline',            flag:'🇪🇸', tz:'Europe/Madrid',       lat:41.403,  lon:2.174,    q:["barcelona live webcam","barcelona en directo camara"], cams:['d1Hqy8eNi9w'] },
  { id:'seoul',  name:'Seúl',             spot:'Río Han',            flag:'🇰🇷', tz:'Asia/Seoul',          lat:37.528,  lon:126.932,  q:["seoul han river live cam","seoul live camera 24/7"], cams:['RVvYQghFCXk','vk5BHoDxXf0'] },
  { id:'sydney', name:'Sídney',           spot:'Bahía y Ópera',      flag:'🇦🇺', tz:'Australia/Sydney',    lat:-33.857, lon:151.215,  q:["sydney harbour live cam","sydney opera house live webcam"], cams:['U2UfQH4lHAY','5uZa3-RMFos'] },
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
