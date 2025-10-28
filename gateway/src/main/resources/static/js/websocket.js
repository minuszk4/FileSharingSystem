const nodeList = document.getElementById('nodeList');
if (nodeList) {
    const ws = new WebSocket(`ws://${window.location.host}/ws/nodes`);

    ws.onopen = () => console.log("Connected to WebSocket for nodes");

    ws.onmessage = (event) => {
        const nodes = JSON.parse(event.data);
        nodeList.innerHTML = '';
        nodes.forEach(node => {
            const li = document.createElement('li');
            li.textContent = `${node.id} - ${node.ip}:${node.port}`;
            nodeList.appendChild(li);
        });
    };

    ws.onclose = () => console.log("WebSocket disconnected");
};
