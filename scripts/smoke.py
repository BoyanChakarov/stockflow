"""Fresh-Compose and restart acceptance using only Python's standard library.

Run only against a disposable/synthetic demo. Credentials come from environment.
"""
import http.cookiejar
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.environ.get('STOCKFLOW_URL', 'http://localhost:8080')
STATE = Path(os.environ.get('STOCKFLOW_SMOKE_STATE', 'target/smoke-state.json'))


class Client:
    def __init__(self, username, password):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        token = self.get('/api/v1/csrf')
        form = urllib.parse.urlencode({'username': username, 'password': password, '_csrf': token['token']}).encode()
        response = self.http.open(urllib.request.Request(BASE + '/login', data=form))
        assert '/login' not in response.url, 'Login failed'
        assert self.get('/api/v1/me')['username'] == username

    def request(self, path, method='GET', body=None, key=None, expected=200):
        headers = {'Accept': 'application/json'}
        data = None
        if method != 'GET':
            token = self.get('/api/v1/csrf')
            headers[token['headerName']] = token['token']
            if body is not None:
                data = json.dumps(body).encode()
                headers['Content-Type'] = 'application/json'
            if key:
                headers['Idempotency-Key'] = key
        try:
            response = self.http.open(urllib.request.Request(BASE + path, data=data, headers=headers, method=method), timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        parsed = json.load(response)
        assert response.status == expected, (path, response.status, parsed)
        return parsed

    def get(self, path):
        return self.request(path)

    def post(self, path, body=None, key=None, expected=200):
        return self.request(path, 'POST', body, key, expected)


admin = Client('admin', os.environ['STOCKFLOW_ADMIN_PASSWORD'])
staff = Client('staff', os.environ['STOCKFLOW_STAFF_PASSWORD'])
if '--check-restart' in sys.argv:
    saved = json.loads(STATE.read_text())
    assert staff.post('/api/v1/orders', saved['payload'], saved['key'], 201) == saved['original']
    assert staff.get('/api/v1/orders/' + saved['original']['id'])['status'] == 'FULFILLED'
    current = staff.get('/api/v1/products/' + saved['product'])
    assert (current['onHand'], current['reserved'], current['available']) == (7, 0, 7)
    assert admin.get('/api/v1/inventory/movements?productId=' + saved['product'])['totalElements'] == 5
    print('PASS: restart retained balances, history, accounts, and original idempotency response')
    sys.exit(0)

product = admin.post('/api/v1/products', {'sku': 'SMOKE-' + uuid.uuid4().hex[:12].upper(), 'name': 'Acceptance mug', 'unitPrice': '12.50', 'reorderThreshold': 2}, expected=201)['id']
customer = staff.post('/api/v1/customers', {'name': 'Acceptance customer', 'email': 'smoke@example.com'}, expected=201)['id']
receipt = {'productId': product, 'quantity': 10, 'reason': 'Compose acceptance opening stock'}
receipt_key = str(uuid.uuid4())
first_receipt = admin.post('/api/v1/inventory/receipts', receipt, receipt_key, 201)
assert admin.post('/api/v1/inventory/receipts', receipt, receipt_key, 201) == first_receipt
payload = {'customerId': customer, 'items': [{'productId': product, 'quantity': 3}]}
key = str(uuid.uuid4())
original = staff.post('/api/v1/orders', payload, key, 201)
assert original['total'] == '37.50'
assert staff.post('/api/v1/orders', payload, key, 201) == original
current = staff.get('/api/v1/products/' + product)
assert (current['onHand'], current['reserved'], current['available']) == (10, 3, 7)
staff.post('/api/v1/orders/' + original['id'] + '/fulfil')
staff.post('/api/v1/orders/' + original['id'] + '/fulfil')
second = staff.post('/api/v1/orders', {'customerId': customer, 'items': [{'productId': product, 'quantity': 2}]}, str(uuid.uuid4()), 201)
staff.post('/api/v1/orders/' + second['id'] + '/cancel', {'reason': 'Acceptance cancellation'})
error = staff.post('/api/v1/orders', {'customerId': customer, 'items': [{'productId': product, 'quantity': 8}]}, str(uuid.uuid4()), 409)
assert error['code'] == 'INSUFFICIENT_STOCK'
assert staff.post('/api/v1/inventory/receipts', receipt, str(uuid.uuid4()), 403)['code'] == 'FORBIDDEN'
assert len(staff.get('/api/v1/orders/' + original['id'] + '/events')) == 2
current = staff.get('/api/v1/products/' + product)
assert (current['onHand'], current['reserved'], current['available']) == (7, 0, 7)
STATE.parent.mkdir(parents=True, exist_ok=True)
STATE.write_text(json.dumps({'payload': payload, 'key': key, 'original': original, 'product': product}))
print('PASS: sessions, CSRF, roles, receipt replay, reservation, order replay, fulfilment, cancellation, shortage and ledger')
