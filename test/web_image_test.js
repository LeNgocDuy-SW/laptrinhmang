// No browser dependencies: exercise real rendering and send handlers with a small DOM stub.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const rows = [];
const elements = {
    messagesContainer: { scrollHeight: 500, scrollTop: 500, clientHeight: 300,
        appendChild(row) { rows.push(row); } },
    replyPreviewBar: { style: {} },
    btnSendImage: { disabled: false, setAttribute() {}, removeAttribute() {} }
};
let sent = [];
let alerts = [];
let reader;
const context = vm.createContext({
    window: { addEventListener() {} },
    document: {
        getElementById(id) { return elements[id]; },
        createElement() { return { innerHTML: '',
            querySelector(selector) { return selector === '.reply-btn-inline' ? {} : null; } }; }
    },
    alert(message) { alerts.push(message); },
    FileReader: class { constructor() { reader = this; } readAsDataURL() {} },
    fetch: async (url, request) => {
        sent.push(JSON.parse(request.body));
        return { json: async () => ({ success: true, time: '12:00' }) };
    },
    console
});
vm.runInContext(fs.readFileSync(require('node:path').join(__dirname, '../web/app.js'), 'utf8'), context);
const run = code => vm.runInContext(code, context);
(async () => {
    run('appendMessageBubble("alice", "USER", "12:00", "[[REPLY|bob|photo]][[IMAGE|image/png|aGVsbG8=]]", false)');
    assert.match(rows[0].innerHTML, /src="data:image\/png;base64,aGVsbG8="/);
    assert.match(rows[0].innerHTML, /msg-quote-box/);
    context.appendMessageBubble("alice", "USER", "12:00", '[[IMAGE|image/png|bad" onerror=alert(1)]]', false);
    assert.doesNotMatch(rows[1].innerHTML, /src="data:/);
    assert.doesNotMatch(rows[1].innerHTML, /onerror=/);
    run('state.currentUser="alice"; state.activeRoom="original"; state.replyTarget={sender:"bob",snippet:"hi"}');
    const pending = run('sendSelectedImage({target:{value:"photo.png",files:[{type:"image/png",size:100}]}})');
    assert.equal(elements.btnSendImage.disabled, true);
    run('state.activeRoom="different"');
    reader.result = 'data:image/png;base64,aGVsbG8=';
    reader.onload();
    await pending;
    assert.equal(sent[0].groupName, 'original');
    assert.equal(sent[0].content, '[[REPLY|bob|hi]][[IMAGE|image/png|aGVsbG8=]]');
    assert.equal(rows.length, 2, 'Do not render outgoing photo in a different room');
    assert.equal(elements.btnSendImage.disabled, false);
    await run('sendSelectedImage({target:{value:"huge.png",files:[{type:"image/png",size:2097153}]}})');
    assert.equal(sent.length, 1);
    assert.equal(alerts.length, 1);
    run('state.activeRoom="original"; state.renderedMessages.add("alice|hello|12:00")');
    await run('sendPayloadContent("hello")');
    assert.equal(rows.length, 2, 'Do not duplicate message already received via SSE');
    console.log('[PASS] Web image rendering, reply, invalid data, size limit, room changes and duplicate suppression');
})().catch(error => { console.error(error); process.exitCode = 1; });
