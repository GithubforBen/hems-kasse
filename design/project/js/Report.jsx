// Report.jsx — Abschluss-Bericht / Kassenstand
const { useState, useMemo } = React;

function Report({sales, openingCash, onSetOpening, onCloseShift, user, shiftStartedAt}){
  const [counts, setCounts] = useState(
    () => Object.fromEntries(DENOMS.map(d=>[d.v,0]))
  );
  const [notes, setNotes] = useState('');

  const counted = DENOMS.reduce((s,d)=>s+d.v*(parseInt(counts[d.v])||0),0);

  const cashSales = sales.filter(s=>s.method==='Bar').reduce((s,x)=>s+x.total,0);
  const cardSales = sales.filter(s=>s.method==='Karte').reduce((s,x)=>s+x.total,0);
  const totalSales = cashSales + cardSales;
  const expected = (parseFloat(openingCash)||0) + cashSales;
  const diff = +(counted - expected).toFixed(2);
  const itemsSold = sales.reduce((s,x)=>s+x.items.reduce((q,i)=>q+i.qty,0),0);

  const top = useMemo(()=>{
    const m = {};
    sales.forEach(s=>s.items.forEach(i=>{
      if(!m[i.name]) m[i.name] = {qty:0,sum:0};
      m[i.name].qty += i.qty;
      m[i.name].sum += i.qty*i.price;
    }));
    return Object.entries(m).sort((a,b)=>b[1].qty-a[1].qty).slice(0,8);
  },[sales]);

  const topMaxQty = top[0] ? top[0][1].qty : 1;

  const shiftStart = shiftStartedAt ? new Date(shiftStartedAt) : null;
  const fmtTime = d => d ? d.toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'}) : '–';

  return (
    <div className="scroll-y" style={{flex:1}}>
    <div className="report">

      {/* KPI */}
      <div className="stat-grid">
        <div className="stat">
          <div className="l">Umsatz gesamt</div>
          <div className="v">{EUR(totalSales)}</div>
          <div className="s">{sales.length} Bons · {itemsSold} Artikel</div>
        </div>
        <div className="stat">
          <div className="l">Bar</div>
          <div className="v">{EUR(cashSales)}</div>
          <div className="s">{sales.filter(s=>s.method==='Bar').length} Zahlungen</div>
        </div>
        <div className="stat">
          <div className="l">Karte</div>
          <div className="v">{EUR(cardSales)}</div>
          <div className="s">{sales.filter(s=>s.method==='Karte').length} Zahlungen</div>
        </div>
        <div className="stat">
          <div className="l">Ø Bon</div>
          <div className="v">{EUR(sales.length ? totalSales/sales.length : 0)}</div>
          <div className="s">seit {fmtTime(shiftStart)} Uhr</div>
        </div>
      </div>

      <div className="two-col">
        {/* Kassenzählung */}
        <div className="card-box">
          <h3>
            <span>Kassenzählung</span>
            <span className="meta">{counted.toFixed(2).replace('.',',')} € gezählt</span>
          </h3>

          <div style={{display:'flex',gap:10,alignItems:'flex-end',marginBottom:12}}>
            <div style={{flex:1}}>
              <label className="label">Anfangsbestand (Wechselgeld)</label>
              <input className="input" inputMode="decimal" style={{padding:'10px 12px'}}
                     value={openingCash}
                     onChange={e=>onSetOpening(e.target.value.replace(/[^\d,.]/g,'').replace('.',','))}
                     placeholder="0,00" />
            </div>
          </div>

          {DENOMS.slice().reverse().map(d=>{
            const n = parseInt(counts[d.v])||0;
            return (
              <div key={d.v} className="denom-row">
                <div className="d">{d.l}</div>
                <input type="number" min="0" value={counts[d.v]||''}
                       placeholder="0"
                       onChange={e=>setCounts({...counts,[d.v]:e.target.value.replace(/[^\d]/g,'')})} />
                <div className="sub">{EUR(d.v*n)}</div>
              </div>
            );
          })}
        </div>

        {/* Auswertung */}
        <div>
          <div className="card-box" style={{marginBottom:14}}>
            <h3>Tagesabschluss</h3>
            <div className="summary-list">
              <div className="l">
                <span>Anfangsbestand</span>
                <span className="v">{EUR(parseFloat((openingCash||'').toString().replace(',','.'))||0)}</span>
              </div>
              <div className="l">
                <span>+ Barumsatz</span>
                <span className="v">{EUR(cashSales)}</span>
              </div>
              <div className="l tot">
                <span>Soll-Bestand Bar</span>
                <span className="v">{EUR(expected)}</span>
              </div>
              <div className="l">
                <span>Ist-Bestand (gezählt)</span>
                <span className="v">{EUR(counted)}</span>
              </div>
            </div>
            <div className={'diff '+(diff===0?'zero':(diff>0?'ok':'bad'))}>
              <span className="l">
                {diff===0 ? '✓ Kasse stimmt' : diff>0 ? '↑ Überschuss' : '↓ Fehlbetrag'}
              </span>
              <span className="v">{diff>0?'+':''}{EUR(diff)}</span>
            </div>

            <label className="label" style={{marginTop:14}}>Anmerkungen (optional)</label>
            <textarea className="input" rows="2"
                      value={notes} onChange={e=>setNotes(e.target.value)}
                      placeholder="z. B. Spende, beschädigtes Geld, Übergabe an…" />

            <div className="report-actions">
              <button className="btn secondary" onClick={()=>window.print()}>Drucken</button>
              <button className="btn danger"
                      onClick={()=>onCloseShift({counted, expected, diff, notes, top, totals:{cashSales,cardSales,totalSales,itemsSold}})}>
                Schicht abschließen
              </button>
            </div>
          </div>

          <div className="card-box">
            <h3>
              <span>Top-Verkäufer</span>
              <span className="meta">{itemsSold} Artikel insg.</span>
            </h3>
            {top.length===0 ? (
              <div className="report-empty">Noch keine Verkäufe in dieser Schicht.</div>
            ) : (
              <div className="top-list">
                {top.map(([name,d],i)=>(
                  <div key={name} className="t-row">
                    <span className="rk">{i+1}</span>
                    <div>
                      <div style={{fontWeight:550}}>{name}</div>
                      <div className="bar-bg"><div className="bar" style={{width:(d.qty/topMaxQty*100)+'%'}}></div></div>
                    </div>
                    <span className="qy">×{d.qty}</span>
                    <span className="sm">{EUR(d.sum)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
    </div>
  );
}

window.Report = Report;