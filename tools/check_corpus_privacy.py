"""Refuse publication of licensed corpora or collection/build artifacts."""
import subprocess
import sys


def is_private_path(path):
    return (path.startswith(('docs/暨大/', 'docs/corpus-review/jnu-v', 'app/src/main/assets/corpus_jnu_'))
            or path.lower().endswith(('.apk','.aab','.zip','.db','.wav','.pcm.tmp')))


def private_paths_in_history(ref):
    # Check all reachable history: deleting a file in a later commit does not remove it.
    result=subprocess.run(['git','-c','core.quotePath=false','rev-list','--objects',ref],check=True,capture_output=True,text=True)
    return sorted({line.split(' ',1)[1] for line in result.stdout.splitlines()
                   if ' ' in line and is_private_path(line.split(' ',1)[1])})


def main():
    if '--pre-push' in sys.argv:
        refs=[fields[1] for line in sys.stdin for fields in [line.split()]
              if len(fields)==4 and set(fields[1])!={'0'}]
    else:refs=sys.argv[1:] or ['HEAD']
    try:
        blocked={p for ref in refs for p in private_paths_in_history(ref)}
    except (OSError,subprocess.CalledProcessError) as exc:
        print('Privacy check failed; push stopped: '+str(exc),file=sys.stderr);return 1
    if blocked:
        print('Push stopped: licensed corpus or collection artifacts exist in reachable history.',file=sys.stderr)
        for path in sorted(blocked)[:5]:print('  '+path,file=sys.stderr)
        return 1
    return 0

if __name__=='__main__':raise SystemExit(main())
