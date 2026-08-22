import requests
import uuid

URL = 'https://dqolfqdeydhmjlnefbzj.supabase.co'
KEY = 'sb_publishable_qe-gAp22qJUikSUaZQQplA_CU-cGP_1'

r = requests.post(
    f'{URL}/auth/v1/signup',
    headers={'apikey': KEY, 'Content-Type': 'application/json'},
    json={'data': {'device_id': 'diag-recall-001'}},
)
tok = r.json()['access_token']
H = {'apikey': KEY, 'Authorization': f'Bearer {tok}', 'Content-Type': 'application/json'}

rk = 'recall-key-' + uuid.uuid4().hex[:8]
r = requests.post(
    f'{URL}/rest/v1/rpc/pair_device',
    headers=H,
    json={'room_key': rk, 'participant_role': 'A', 'device_id': 'diag-dev'},
)
room = r.json()[0]['room_id']
print('room:', room)

mid = str(uuid.uuid4())
msg = {
    'id': mid,
    'conversation_id': room,
    'sender_id': 'diag-dev',
    'sender_role': 'A',
    'type': 'TEXT',
    'text': '原文内容',
    'created_at_ms': 1787384000000,
}
r = requests.post(f'{URL}/rest/v1/messages', headers={**H, 'Prefer': 'return=minimal'}, json=[msg])
print('插入原文:', r.status_code, r.text[:200])

# 模拟撤回：同 id，text=null，recalled_at_ms
recall = {
    'id': mid,
    'conversation_id': room,
    'sender_id': 'diag-dev',
    'sender_role': 'A',
    'type': 'TEXT',
    'text': None,
    'created_at_ms': 1787384000000,
    'recalled_at_ms': 1787384100000,
}
r = requests.post(
    f'{URL}/rest/v1/messages?on_conflict=id',
    headers={**H, 'Prefer': 'resolution=merge-duplicates,return=minimal'},
    json=[recall],
)
print('撤回upsert:', r.status_code, r.text[:300])

r = requests.get(f'{URL}/rest/v1/messages?id=eq.{mid}&select=text,recalled_at_ms', headers=H)
print('查询撤回后:', r.status_code, r.text[:300])
