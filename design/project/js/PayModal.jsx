// PayModal.jsx — Bezahlflow (Bar + Karte) + Beleg
const { useState, useEffect } = React;

function PayModal({total, items, onClose, onComplete}){
  const [stage, setStage] = useState('choose'); // choose | cash | card | done
  const [given, setGiven] = useState('');
  const [doneInfo, setDoneInfo] = useState(null);
  const givenNum = parseFloat(given.replace(',','.'))||0;
  const change = givenNum - total;

  const kp = (k) => {
    if(k==='⌫'){ setGiven(g=>g.slice(0,-1)); return; }
    if(k===','){ if(!given.includes(',')) setGiven(g=>(g||'0')+','); return; }
    setGiven(g=>{
      const next = g+k;
      if(next.includes(',')){
        const [a,b]=next.split(',');
        if(b && b.length>2) return g;
      }
      return next;
    });
  };

  // Denomination tap-to-add ("ich habe einen 5€-Schein bekommen“)
  const addDenom = (v) => {
    setGiven(g => {
      const cur = parseFloat((g||'0').replace(',','.'))||0;
      const next = +(cur + v).toFixed(2);
      return next.toFixed(2).replace('.',',');
    });
  };
  const clearGiven = () => setGiven('');

  const ceil = n => Math.ceil(n*100)/100;
  const quickRaw = [
    ceil(total),
    Math.ceil(total),
    Math.ceil(total/5)*5,
    Math.ceil(total/10)*10,
  ];
  const uniqQuick = [...new Map(quickRaw.map(v=>[v.toFixed(2), v])).values()].slice(0,4);

  const finishCash = () => {
    if(given==='' || givenNum < total) return;
    const info = {method:'Bar', total, given:givenNum, change};
    setDoneInfo(info);
    onComplete(info);
    setStage('done');
  };

  // Karten-Flow automatisch
  useEffect(()=>{
    if(stage==='card'){
      const t = setTimeout(()=>{
        const info = {method:'Karte', total, given:total, change:0};
        setDoneInfo(info);
        onComplete(info);
        setStage('done');
      }, 1800);
      return ()=>clearTimeout(t);
    }
  },[stage]);

  return (
    <div className="modal-bg" onMouseDown={e=>{if(e.target===e.currentTarget && stage!=='card') onClose()}}>
      <div className={'modal '+((stage==='cash')?'wide':'')}>
        {stage==='choose' && <ChooseStage total={total} items={items}
                                          onCash={()=>setStage('cash')}
                                          onCard={()=>setStage('card')}
                                          onClose={onClose} />}
        {stage==='cash'   && <CashStage total={total} given={given} setGiven={setGiven}
                                        givenNum={givenNum} change={change}
                                        addDenom={addDenom} clearGiven={clearGiven}
                                        kp={kp}
                                        onBack={()=>setStage('choose')}
                                        onConfirm={finishCash} />}
        {stage==='card'   && <CardStage total={total} onCancel={()=>setStage('choose')} />}
        {stage==='done'   && <DoneStage info={doneInfo} items={items} onClose={onClose} />}
      </div>
    </div>
  );
}

function ChooseStage({total, items, onCash, onCard, onClose}){
  const qty = items.reduce((s,x)=>s+x.qty,0);
  return (<>
    <div className="modal-h">
      <h3>Bezahlen</h3>
      <p>{qty} Artikel · Bitte Zahlungsmethode wählen</p>
    </div>
    <div className="modal-b">
      <div className="pay-summary">
        <span className="l">Zu zahlen</span>
        <span className="v">{EUR(total)}</span>
      </div>
      <div className="pay-row">
        <button className="pay-btn pay-cash" onClick={onCash}>
          <span className="ico">💶</span>
          <span>Bar</span>
          <span className="lbl">Bargeld zählen</span>
        </button>
        <button className="pay-btn pay-card" onClick={onCard}>
          <span className="ico">💳</span>
          <span>Karte</span>
          <span className="lbl">Kontaktlos / EC</span>
        </button>
      </div>
    </div>
    <div className="modal-f">
      <button className="btn ghost" onClick={onClose}>Abbrechen</button>
    </div>
  </>);
}

function CashStage({total, given, setGiven, givenNum, change, addDenom, clearGiven, kp, onBack, onConfirm}){
  // Münzen + Scheine: groß = häufige, immer in einer logischen Reihenfolge
  const notes = [
    {v:5,   l:'5\u00a0\u20ac',  cls:'note note-5'},
    {v:10,  l:'10\u00a0\u20ac', cls:'note note-10'},
    {v:20,  l:'20\u00a0\u20ac', cls:'note note-20'},
    {v:50,  l:'50\u00a0\u20ac', cls:'note note-50'},
    {v:100, l:'100\u00a0\u20ac',cls:'note note-100'},
  ];
  const coins = [
    {v:0.05, l:'5\u00a0ct', cls:'coin coin-cu'},
    {v:0.10, l:'10\u00a0ct',cls:'coin coin-cu'},
    {v:0.20, l:'20\u00a0ct',cls:'coin coin-cu'},
    {v:0.50, l:'50\u00a0ct',cls:'coin coin-au'},
    {v:1,    l:'1\u00a0\u20ac', cls:'coin coin-eu1'},
    {v:2,    l:'2\u00a0\u20ac', cls:'coin coin-eu2'},
  ];

  return (<>
    <div className="modal-h">
      <h3>Bar bezahlen</h3>
      <p>Auf Geldsymbole tippen oder Betrag eingeben.</p>
    </div>
    <div className="modal-b">
      <div className="pay-summary">
        <span className="l">Zu zahlen</span>
        <span className="v">{EUR(total)}</span>
      </div>

      {/* Gegeben + Quick clear */}
      <div className="given-row">
        <div className="given-field">
          <span className="given-l">Gegeben</span>
          <input className="given-input" inputMode="decimal" placeholder="0,00"
                 value={given}
                 onChange={e=>setGiven(e.target.value.replace(/[^\d,.]/g,'').replace('.',','))} />
        </div>
        <button className="btn-clear" onClick={clearGiven} title="Zurücksetzen">↻</button>
      </div>

      <div className="money-section">
        <div className="money-label">Scheine</div>
        <div className="money-grid notes-grid">
          {notes.map(n=>(
            <button key={n.v} className={n.cls} onClick={()=>addDenom(n.v)}>
              <span className="v">{n.l}</span>
            </button>
          ))}
        </div>
        <div className="money-label">Münzen</div>
        <div className="money-grid coins-grid">
          {coins.map(c=>(
            <button key={c.v} className={c.cls} onClick={()=>addDenom(c.v)}>
              <span className="v">{c.l}</span>
            </button>
          ))}
        </div>
      </div>

      {(given!=='' && givenNum>0) && (
        <div className={'change-line '+(change<0?'bad':'')}>
          <span className="l">{change<0?'Es fehlen':'Rückgeld'}</span>
          <span className="v">{EUR(Math.abs(change))}</span>
        </div>
      )}
    </div>
    <div className="modal-f">
      <button className="btn ghost" onClick={onBack}>← Zurück</button>
      <button className="btn ok" disabled={given==='' || givenNum<total} onClick={onConfirm}>
        Bezahlung bestätigen
      </button>
    </div>
  </>);
}

function CardStage({total, onCancel}){
  return (<>
    <div className="modal-h">
      <h3>Kartenzahlung</h3>
      <p>Karte an das Lesegerät halten…</p>
    </div>
    <div className="modal-b">
      <div className="card-anim">
        <div className="lab">Betrag</div>
        <div className="v">{EUR(total)}</div>
        <div className="status"><span className="pulse"></span>Verbindung mit Terminal…</div>
      </div>
    </div>
    <div className="modal-f">
      <button className="btn ghost" onClick={onCancel}>Abbrechen</button>
    </div>
  </>);
}

function DoneStage({info, items, onClose}){
  const {total, given, change, method} = info || {};
  return (<>
    <div className="modal-h">
      <div className="success-ic">✓</div>
      <h3 style={{textAlign:'center'}}>Bezahlt</h3>
      <p style={{textAlign:'center'}}>{method} · {EUR(total)} · Vielen Dank!</p>
    </div>
    <div className="modal-b">
      <div className="receipt">
        <div className="h">
          <div className="t1">SCHULKASSE · KUCHENVERKAUF</div>
          <div className="t2">{new Date().toLocaleString('de-DE')}</div>
        </div>
        {items.map(it=>(
          <div className="r" key={it.id}>
            <span>{it.qty}× {it.name}</span>
            <span>{EUR(it.price*it.qty)}</span>
          </div>
        ))}
        <div className="sep"></div>
        <div className="r grand"><span>GESAMT</span><span>{EUR(total)}</span></div>
        <div className="r"><span>Zahlung</span><span>{method}</span></div>
        {method==='Bar' && given>0 && (<>
          <div className="r"><span>Gegeben</span><span>{EUR(given)}</span></div>
          <div className="r"><span>Rückgeld</span><span>{EUR(change)}</span></div>
        </>)}
      </div>
    </div>
    <div className="modal-f">
      <button className="btn" onClick={onClose}>Weiter verkaufen →</button>
    </div>
  </>);
}

window.PayModal = PayModal;