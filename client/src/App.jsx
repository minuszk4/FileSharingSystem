import React, { useState } from "react";

function App() {
    const [file, setFile] = useState(null);
    const [message, setMessage] = useState("");

    const handleFileChange = (e) => {
        setFile(e.target.files[0]);
    };

    const handleUpload = async () => {
        if (!file) {
            alert("Please select a file first!");
            return;
        }

        const formData = new FormData();
        formData.append("file", file);

        try {
            console.log("Uploading file:", file.name);

            const response = await fetch("http://localhost:8080/upload", {
                method: "POST",
                body: formData,
            });

            console.log("Response status:", response.status);

            if (!response.ok) {
                throw new Error(`Server error: ${response.status}`);
            }

            const result = await response.json(); // ← Đổi thành .json() thay vì .text()
            console.log("Upload result:", result);

            setMessage(`✅ Upload success! InfoHash: ${result.infoHash || JSON.stringify(result)}`);

        } catch (error) {
            console.error("Upload error:", error);
            setMessage("❌ Upload failed: " + error.message);
        }
    };

    return (
        <div style={{ padding: "40px", textAlign: "center" }}>
            <h1>📤 Upload File</h1>
            <input type="file" onChange={handleFileChange} />
            <button
                onClick={handleUpload}
                style={{ marginLeft: "10px", padding: "6px 12px" }}
            >
                Upload
            </button>
            <p>{message}</p>
        </div>
    );
}

export default App;
