"""Original soft tactile UI cues, MIT. No sampled or third-party audio.
Reproducible 44.1 kHz mono PCM: damped material resonances plus filtered contact noise.
No echo, delayed notes, pitch bends or musical chord progression.
"""
from pathlib import Path
import math, wave, struct, json, hashlib, argparse, random
RATE=44100


def cue(duration,body,decay,peak,seed):
    rng=random.Random(seed)
    low=low2=dc=0.0
    a=1-math.exp(-2*math.pi*1050/RATE)
    hp=1-math.exp(-2*math.pi*90/RATE)
    samples=[]
    for i in range(round(duration*RATE)):
        t=i/RATE
        low+=a*(rng.uniform(-1,1)-low)
        low2+=a*(low-low2)
        dc+=hp*(low2-dc)
        contact=(low2-dc)*1.1*math.exp(-t/.012)
        # Inharmonic, rapidly damped material modes; not a held musical interval.
        resonances=(.85*math.sin(2*math.pi*body*t)*math.exp(-t/decay)
                    +.19*math.sin(2*math.pi*body*2.57*t)*math.exp(-t/(decay*.48))
                    +.045*math.sin(2*math.pi*body*4.19*t)*math.exp(-t/(decay*.25)))
        attack=math.sin(min(1,t/.005)*math.pi/2)**2
        release=min(1,max(0,(duration-t)/.025))**2
        samples.append((resonances+contact)*attack*release)
    # Remove the small residual DC component without discontinuities at either endpoint.
    weights=[math.sin(math.pi*i/(len(samples)-1))**2 for i in range(len(samples))]
    dc=sum(samples)/sum(weights)
    samples=[x-dc*w for x,w in zip(samples,weights)]
    gain=peak/max(abs(x) for x in samples)
    samples=[x*gain for x in samples];samples[0]=samples[-1]=0
    return samples


def write(path,samples):
    data=struct.pack('<'+'h'*len(samples),*(round(v*32767) for v in samples))
    with wave.open(str(path),'wb') as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(RATE);w.writeframes(data)
    return dict(file=path.name,seconds=len(samples)/RATE,sample_rate=RATE,channels=1,
                peak=max(abs(v) for v in samples),rms=math.sqrt(sum(v*v for v in samples)/len(samples)),
                sha256=hashlib.sha256(path.read_bytes()).hexdigest())


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'app/src/main/res/raw')
    parser.add_argument('--preview',type=Path)
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    move=cue(.095,260,.019,.72,4601)
    select=cue(.16,205,.032,.76,4602)
    metrics=[write(args.output/'cinema_move.wav',move),write(args.output/'cinema_select.wav',select)]
    if args.preview:
        args.preview.parent.mkdir(parents=True,exist_ok=True)
        silence=[0.0]*round(.6*RATE)
        # Match the existing SoundPool per-cue gains; device media volume still applies.
        move_preview=[x*.65 for x in move];select_preview=[x*.85 for x in select]
        write(args.preview,move_preview+silence+move_preview+silence+select_preview)
    print(json.dumps(metrics,indent=2))
