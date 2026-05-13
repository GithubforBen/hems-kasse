// Login.jsx
const { useState } = React;

function Login({onLogin, recent}){
  const [name,setName] = useState('');
  const [klasse,setKlasse] = useState('');
  const [role,setRole] = useState('verkauf'); // verkauf | admin

  const submit = () => {
    if(!name.trim()) return;
    onLogin({name:name.trim(), klasse:klasse.trim(), role, startedAt:Date.now()});
  };

  return (
    <div className="login-wrap">
      <div className="login-card">
        <div className="brand">
          <div className="brand-mark">K</div>
          <div className="brand-text">
            <div className="t1">Schulkasse</div>
            <div className="t2">Kuchenverkauf · Anmeldung</div>
          </div>
        </div>

        <div style={{marginBottom:12}}>
          <label className="label">Name</label>
          <input className="input" autoFocus value={name}
                 onChange={e=>setName(e.target.value)}
                 onKeyDown={e=>e.key==='Enter'&&submit()}
                 placeholder="z. B. Lena Müller" />
        </div>

        <div style={{marginBottom:14}}>
          <label className="label">Klasse / Rolle (optional)</label>
          <input className="input" value={klasse}
                 onChange={e=>setKlasse(e.target.value)}
                 onKeyDown={e=>e.key==='Enter'&&submit()}
                 placeholder="z. B. 9b · Elternbeirat" />
        </div>

        <div>
          <label className="label">Anmelden als</label>
          <div className="role-pick">
            <label className={role==='verkauf'?'sel':''}>
              <input type="radio" name="role" value="verkauf"
                     checked={role==='verkauf'} onChange={()=>setRole('verkauf')} />
              <span>👋 Verkäufer:in</span>
            </label>
            <label className={role==='admin'?'sel':''}>
              <input type="radio" name="role" value="admin"
                     checked={role==='admin'} onChange={()=>setRole('admin')} />
              <span>🔑 Admin</span>
            </label>
          </div>
        </div>

        <button className="btn big" style={{marginTop:18}} onClick={submit}>
          Schicht beginnen →
        </button>

        {recent && recent.length>0 && (
          <>
            <div style={{marginTop:18,fontSize:11.5,color:'var(--ink-3)',fontWeight:600,textTransform:'uppercase',letterSpacing:'.06em'}}>
              Zuletzt angemeldet
            </div>
            <div className="recent-users">
              {recent.map((r,i)=>(
                <button key={i} className="chip"
                        onClick={()=>onLogin({name:r.name, klasse:r.klasse, role:r.role||'verkauf', startedAt:Date.now()})}>
                  {r.name}{r.klasse?' · '+r.klasse:''}
                </button>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

window.Login = Login;