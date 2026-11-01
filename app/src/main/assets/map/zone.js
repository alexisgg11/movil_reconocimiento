const map=L.map('map',{zoomControl:true,attributionControl:true}).setView([-9.19,-75.015],5);
map.attributionControl.setPrefix(false);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
  maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
}).addTo(map);
const pin=L.marker([0,0],{draggable:true,icon:L.divIcon({className:'zone-pin',html:'<span></span>',iconSize:[30,36],iconAnchor:[15,33]})});
const circle=L.circle([0,0],{radius:100,color:'#1474d4',weight:2,fillColor:'#1474d4',fillOpacity:.15});
let chosen=false,radius=100,lastLat=null,lastLon=null;
window.resizeZone=function(height){
  const pixels=Math.max(1,height||window.innerHeight);
  document.documentElement.style.height=pixels+'px';
  document.body.style.height=pixels+'px';
  document.getElementById('map').style.height=pixels+'px';
  map.invalidateSize();
};
function publish(point){
  const first=!chosen;
  chosen=true;lastLat=point.lat;lastLon=point.lng;
  if(!map.hasLayer(circle))circle.addTo(map);
  if(!map.hasLayer(pin))pin.addTo(map);
  pin.setLatLng(point);circle.setLatLng(point);
  if(first)map.fitBounds(circle.getBounds().pad(.5),{maxZoom:18,animate:false});
  document.getElementById('hint').textContent='Arrastra el pin para ajustar el punto';
  ZoneBridge.onPoint(point.lat,point.lng);
}
map.on('click',e=>publish(e.latlng));
pin.on('drag',()=>{circle.setLatLng(pin.getLatLng());});
pin.on('dragend',()=>publish(pin.getLatLng()));
window.setZone=function(lat,lon,meters){
  map.invalidateSize();
  const radiusChanged=Math.abs(radius-meters)>.01;
  radius=Math.max(1,Math.min(10000,meters));circle.setRadius(radius);
  if(lat===null||lon===null){return;}
  const changed=lastLat===null||Math.abs(lat-lastLat)>0.0000001||Math.abs(lon-lastLon)>0.0000001;
  chosen=true;lastLat=lat;lastLon=lon;
  pin.setLatLng([lat,lon]);circle.setLatLng([lat,lon]);
  if(!map.hasLayer(circle))circle.addTo(map);
  if(!map.hasLayer(pin))pin.addTo(map);
  if(changed||radiusChanged)map.fitBounds(circle.getBounds().pad(.5),{maxZoom:18,animate:false});
  document.getElementById('hint').textContent='Arrastra el pin para ajustar el punto';
  map.invalidateSize();
};
