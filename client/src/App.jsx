import React, { useState } from "react";

function App() {
    const [file, setFile] = useState(null);
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);
    const [downloading, setDownloading] = useState(false);
    const [finding, setFinding] = useState(false);
    const [infoHash, setInfoHash] = useState("");
    const [foundFile, setFoundFile] = useState(null);

    const API_BASE = process.env.REACT_APP_API_URL || "http://localhost:8080";

    // ---------------- UPLOAD ----------------
    const handleFileChange = (e) => {
        setFile(e.target.files[0]);
        setMessage("");
    };

    const handleUpload = async () => {
        if (!file) {
            alert("Please select a file first!");
            return;
        }

        const formData = new FormData();
        formData.append("file", file);

        setLoading(true);
        setMessage("⏳ Uploading...");

        try {
            const response = await fetch(`${API_BASE}/upload`, {
                method: "POST",
                body: formData,
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Server error: ${response.status} - ${errorText}`);
            }

            const result = await response.json();
            setMessage(`✅ Upload success!\n${JSON.stringify(result, null, 2)}`);

            // Nếu backend trả về infoHash thì tự điền luôn vào input
            if (result.infoHash) setInfoHash(result.infoHash);

        } catch (error) {
            console.error("Upload error:", error);
            setMessage("❌ Upload failed: " + error.message);
        } finally {
            setLoading(false);
        }
    };

    // ---------------- FIND ----------------
    const handleFind = async () => {
        if (!infoHash) {
            alert("Please enter infoHash!");
            return;
        }

        setFinding(true);
        setFoundFile(null);
        setMessage("🔍 Finding file...");

        try {
            const response = await fetch(`${API_BASE}/find?hash=${infoHash}`);

            if (!response.ok) {
                throw new Error("File not found or server error");
            }

            const data = await response.json();
            setFoundFile(data);
            setMessage(`✅ File found: ${data.fileName || "Unknown"}`);
        } catch (error) {
            setFoundFile(null);
            setMessage("❌ " + error.message);
        } finally {
            setFinding(false);
        }
    };

    // ---------------- DOWNLOAD ----------------
    const downloadFile = async (hash) => {
        try {
            const response = await fetch(`${API_BASE}/download/magnet?hash=${hash}`);

            if (!response.ok) throw new Error("Download failed");

            const contentDisposition = response.headers.get("Content-Disposition");
            const filenameMatch = contentDisposition?.match(/filename="(.+)"/);
            const filename = filenameMatch ? filenameMatch[1] : `${hash}.file`;

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        } catch (error) {
            console.error("Download failed:", error);
            alert("❌ Download failed: " + error.message);
        }
    };

    const handleDownload = async () => {
        if (!foundFile) {
            alert("Please find the file first!");
            return;
        }

        setDownloading(true);
        await downloadFile(infoHash);
        setDownloading(false);
    };

    // ---------------- UI ----------------
    return (
        <div style={{ padding: "40px", textAlign: "center" }}>
            <h1>📤 Upload & Download File</h1>

            {/* Upload Section */}
            <div style={{ marginBottom: "20px" }}>
                <input type="file" onChange={handleFileChange} disabled={loading} />
                <button
                    onClick={handleUpload}
                    disabled={loading || !file}
                    style={{
                        marginLeft: "10px",
                        padding: "6px 12px",
                        cursor: loading || !file ? "not-allowed" : "pointer",
                    }}
                >
                    {loading ? "Uploading..." : "Upload"}
                </button>
            </div>

            {file && (
                <div style={{ margin: "10px 0", color: "#666" }}>
                    Selected: {file.name} ({(file.size / 1024).toFixed(2)} KB)
                </div>
            )}

            {/* Find Section */}
            <div style={{ marginTop: "30px" }}>
                <h3>🔍 Find File by InfoHash</h3>
                <input
                    type="text"
                    placeholder="Enter infoHash..."
                    value={infoHash}
                    onChange={(e) => setInfoHash(e.target.value)}
                    style={{
                        padding: "6px",
                        width: "300px",
                        marginRight: "10px",
                    }}
                />
                <button
                    onClick={handleFind}
                    disabled={finding || !infoHash}
                    style={{
                        padding: "6px 12px",
                        cursor: finding || !infoHash ? "not-allowed" : "pointer",
                    }}
                >
                    {finding ? "Finding..." : "Find"}
                </button>
            </div>

            {/* Show file info */}
            {foundFile && (
                <div
                    style={{
                        marginTop: "15px",
                        background: "#eef",
                        padding: "10px",
                        borderRadius: "6px",
                        display: "inline-block",
                    }}
                >
                    <strong>File:</strong> {foundFile.fileName || "Unknown"} <br />
                    <strong>Size:</strong>{" "}
                    {foundFile.size ? (foundFile.size / 1024).toFixed(2) + " KB" : "N/A"}
                </div>
            )}

            {/* Download Section */}
            <div style={{ marginTop: "20px" }}>
                <button
                    onClick={handleDownload}
                    disabled={downloading || !foundFile}
                    style={{
                        padding: "8px 14px",
                        cursor: downloading || !foundFile ? "not-allowed" : "pointer",
                        backgroundColor: foundFile ? "#4CAF50" : "#ccc",
                        color: "white",
                        border: "none",
                        borderRadius: "5px",
                    }}
                >
                    {downloading ? "Downloading..." : "Download"}
                </button>
            </div>

            {/* Message */}
            {message && (
                <pre
                    style={{
                        marginTop: "20px",
                        padding: "15px",
                        backgroundColor: message.includes("✅")
                            ? "#d4edda"
                            : "#f8d7da",
                        borderRadius: "5px",
                        textAlign: "left",
                        whiteSpace: "pre-wrap",
                    }}
                >
                    {message}
                </pre>
            )}
        </div>
    );
}

export default App;
