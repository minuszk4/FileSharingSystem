// Upload form
const uploadForm = document.getElementById('uploadForm');
if (uploadForm) {
    uploadForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fileInput = document.getElementById('fileInput');
        const file = fileInput.files[0];
        if (!file) return;

        const formData = new FormData();
        formData.append("file", file);

        try {
            const res = await fetch('/api/files/upload', {
                method: 'POST',
                body: formData
            });
            const result = await res.json();
            document.getElementById('uploadStatus').innerText = result.message;
        } catch (err) {
            document.getElementById('uploadStatus').innerText = "Upload failed!";
        }
    });
}

// Fetch list of files for download.html
const fileList = document.getElementById('fileList');
if (fileList) {
    fetch('/api/files/list')
        .then(res => res.json())
        .then(files => {
            files.forEach(file => {
                const li = document.createElement('li');
                li.innerHTML = `${file.name} - <a href="/api/files/download/${file.id}">Download</a>`;
                fileList.appendChild(li);
            });
        });
}
