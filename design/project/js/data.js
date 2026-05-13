// data.js — defaults, helpers, persistence
const EUR = n => (n||0).toLocaleString('de-DE',{style:'currency',currency:'EUR'});

const COLORS = [
  {id:'yellow',   cls:'col-yellow',   sw:'sw-yellow',   label:'Sonne'},
  {id:'peach',    cls:'col-peach',    sw:'sw-peach',    label:'Pfirsich'},
  {id:'pink',     cls:'col-pink',     sw:'sw-pink',     label:'Rosa'},
  {id:'mint',     cls:'col-mint',     sw:'sw-mint',     label:'Mint'},
  {id:'lavender', cls:'col-lavender', sw:'sw-lavender', label:'Lavendel'},
  {id:'blue',     cls:'col-blue',     sw:'sw-blue',     label:'Himmel'},
];
const colorCls = id => (COLORS.find(c=>c.id===id) || COLORS[0]).cls;
const swatchCls = id => (COLORS.find(c=>c.id===id) || COLORS[0]).sw;

const DEFAULT_CATS = [
  {id:'kuchen', name:'Kuchen', color:'peach', products:[
    {id:'p1', name:'Schokokuchen', price:1.50, color:'peach'},
    {id:'p2', name:'Apfelkuchen', price:1.50, color:'yellow'},
    {id:'p3', name:'Käsekuchen', price:1.80, color:'yellow'},
    {id:'p4', name:'Marmorkuchen', price:1.20, color:'peach'},
    {id:'p5', name:'Rührkuchen', price:1.00, color:'mint'},
    {id:'p6', name:'Zitronenkuchen', price:1.50, color:'yellow'},
  ]},
  {id:'muffins', name:'Muffins & Kekse', color:'pink', products:[
    {id:'p7', name:'Schoko-Muffin', price:1.00, color:'pink'},
    {id:'p8', name:'Beeren-Muffin', price:1.00, color:'pink'},
    {id:'p9', name:'Cookies (3 Stk.)', price:1.50, color:'peach'},
    {id:'p10', name:'Brownies', price:1.20, color:'peach'},
    {id:'p11', name:'Cake Pop', price:0.80, color:'pink'},
  ]},
  {id:'herzhaft', name:'Herzhaft', color:'mint', products:[
    {id:'p12', name:'Pizzaschnecke', price:1.50, color:'peach'},
    {id:'p13', name:'Käsestange', price:1.00, color:'yellow'},
    {id:'p14', name:'Brezel', price:0.80, color:'yellow'},
    {id:'p15', name:'Mini-Quiche', price:1.80, color:'mint'},
  ]},
  {id:'drinks', name:'Getränke', color:'blue', products:[
    {id:'p16', name:'Wasser 0,5L', price:1.00, color:'blue'},
    {id:'p17', name:'Apfelschorle', price:1.20, color:'mint'},
    {id:'p18', name:'Eistee', price:1.20, color:'lavender'},
    {id:'p19', name:'Kakao', price:1.50, color:'peach'},
  ]},
];

const DENOMS = [
  {v:0.01,l:'1 ct'},{v:0.02,l:'2 ct'},{v:0.05,l:'5 ct'},{v:0.10,l:'10 ct'},
  {v:0.20,l:'20 ct'},{v:0.50,l:'50 ct'},{v:1,l:'1 €'},{v:2,l:'2 €'},
  {v:5,l:'5 €'},{v:10,l:'10 €'},{v:20,l:'20 €'},{v:50,l:'50 €'},{v:100,l:'100 €'},
];

const LS_KEY = 'schulkasse-state-v1';
function loadState(){
  try{ const raw = localStorage.getItem(LS_KEY); if(raw) return JSON.parse(raw); }catch(e){}
  return null;
}
function saveState(s){ try{ localStorage.setItem(LS_KEY, JSON.stringify(s)); }catch(e){} }

const uid = () => 'p' + Math.random().toString(36).slice(2,9);

Object.assign(window, {EUR, COLORS, colorCls, swatchCls, DEFAULT_CATS, DENOMS, loadState, saveState, uid});