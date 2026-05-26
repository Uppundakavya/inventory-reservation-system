import { useState, useEffect } from "react";

export default function App() {
  const [status, setStatus] = useState("idle");
  const [timeLeft, setTimeLeft] = useState(600);
  const [reservationId, setReservationId] = useState(null);

  // State to track when the app is talking to the server
  const [isProcessing, setIsProcessing] = useState(false);

  useEffect(() => {
    if (status === "reserved" && timeLeft > 0) {
      const timer = setTimeout(() => setTimeLeft(timeLeft - 1), 1000);
      return () => clearTimeout(timer);
    }

    if (timeLeft === 0 && status === "reserved") {
      setStatus("cancelled");
      setReservationId(null);
      alert(
        "Reservation expired! The server has returned the item to available stock.",
      );
    }
  }, [status, timeLeft]);

  const handleReserve = async () => {
    setIsProcessing(true); // Disable button immediately
    try {
      const response = await fetch("http://localhost:8082/reserve", {
        method: "POST",
      });
      const data = await response.json();

      if (data.success) {
        // NEW: Log the ID to verify Java is sending it correctly
        console.log("✅ Received Reservation ID:", data.reservationId);

        setStatus("reserved");
        setReservationId(data.reservationId);
        setTimeLeft(600);
      } else {
        setStatus("failed");
      }
    } catch (error) {
      alert(
        "Error: Cannot connect to Java backend. Is Eclipse running on port 8082?",
      );
    } finally {
      setIsProcessing(false); // Re-enable button when done
    }
  };

  const handleConfirm = async () => {
    setIsProcessing(true);

    // NEW FAILSAFE: Don't crash the server if the ID is missing
    if (!reservationId) {
      alert("Error: Reservation ID is missing from React state!");
      setIsProcessing(false);
      return;
    }

    try {
      const response = await fetch(
        `http://localhost:8082/confirm?id=${reservationId}`,
        { method: "POST" },
      );
      const data = await response.json();

      if (data.success) setStatus("confirmed");
    } catch (error) {
      alert("Error processing payment.");
    } finally {
      setIsProcessing(false);
    }
  };

  const handleCancel = async () => {
    setIsProcessing(true);

    // NEW FAILSAFE: Don't crash the server if the ID is missing
    if (!reservationId) {
      alert("Error: Reservation ID is missing from React state!");
      setIsProcessing(false);
      return;
    }

    try {
      const response = await fetch(
        `http://localhost:8082/cancel?id=${reservationId}`,
        { method: "POST" },
      );
      const data = await response.json();

      if (data.success) {
        setStatus("idle");
        setReservationId(null);
      }
    } catch (error) {
      alert("Error cancelling reservation.");
    } finally {
      setIsProcessing(false);
    }
  };

  const formatTime = () => {
    const min = Math.floor(timeLeft / 60);
    const sec = timeLeft % 60;
    return `${min}:${sec < 10 ? "0" : ""}${sec}`;
  };

  return (
    <div style={{ padding: "40px", fontFamily: "Arial" }}>
      <h2>Online Shopping Checkout</h2>

      {status === "idle" && (
        <div
          style={{ border: "1px solid #ccc", padding: "20px", width: "300px" }}
        >
          <h3>Product: iPhone 15</h3>
          <p>Warehouse: Warehouse A</p>

          <button
            onClick={handleReserve}
            disabled={isProcessing}
            style={{
              padding: "10px",
              background: "blue",
              color: "white",
              cursor: isProcessing ? "not-allowed" : "pointer",
              border: "none",
              borderRadius: "5px",
              opacity: isProcessing ? 0.6 : 1,
            }}
          >
            {isProcessing ? "Processing..." : "Reserve Item"}
          </button>
        </div>
      )}

      {status === "reserved" && (
        <div
          style={{
            padding: "20px",
            border: "2px solid orange",
            width: "300px",
          }}
        >
          <h3 style={{ color: "orange" }}>Item Reserved!</h3>

          <p>
            <b>Product:</b> iPhone 15
          </p>
          <p>
            <b>Warehouse:</b> Warehouse A
          </p>
          <p>
            <b>Quantity:</b> 1
          </p>

          <p>Your stock is locked temporarily.</p>
          <p>
            Time remaining to pay:{" "}
            <b style={{ fontSize: "20px", color: "red" }}>{formatTime()}</b>
          </p>
          <div style={{ display: "flex", gap: "10px" }}>
            <button
              onClick={handleConfirm}
              disabled={isProcessing}
              style={{
                padding: "10px",
                background: "green",
                color: "white",
                cursor: isProcessing ? "not-allowed" : "pointer",
                border: "none",
                borderRadius: "5px",
                opacity: isProcessing ? 0.6 : 1,
              }}
            >
              {isProcessing ? "Processing..." : "Confirm Purchase"}
            </button>

            <button
              onClick={handleCancel}
              disabled={isProcessing}
              style={{
                padding: "10px",
                background: "gray",
                color: "white",
                cursor: isProcessing ? "not-allowed" : "pointer",
                border: "none",
                borderRadius: "5px",
                opacity: isProcessing ? 0.6 : 1,
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {status === "confirmed" && (
        <div
          style={{
            padding: "20px",
            border: "2px solid green",
            width: "300px",
            background: "#e8f5e9",
          }}
        >
          <h3 style={{ color: "green" }}>Purchase Successful!</h3>
          <p>Your order is confirmed and stock is permanently reduced.</p>
          <button
            onClick={() => setStatus("idle")}
            style={{ marginTop: "10px", padding: "8px", cursor: "pointer" }}
          >
            Buy Another
          </button>
        </div>
      )}

      {status === "failed" && (
        <div
          style={{ padding: "20px", border: "2px solid red", width: "300px" }}
        >
          <h3 style={{ color: "red" }}>Out of Stock!</h3>
          <p>Sorry, another user bought the last item.</p>
          <button
            onClick={() => setStatus("idle")}
            style={{ marginTop: "10px", padding: "8px", cursor: "pointer" }}
          >
            Go Back
          </button>
        </div>
      )}

      {status === "cancelled" && (
        <div
          style={{ padding: "20px", border: "2px solid gray", width: "300px" }}
        >
          <h3 style={{ color: "gray" }}>Reservation Cancelled</h3>
          <p>The item has been returned to the warehouse.</p>
          <button
            onClick={() => setStatus("idle")}
            style={{ marginTop: "10px", padding: "8px", cursor: "pointer" }}
          >
            Go Back
          </button>
        </div>
      )}
    </div>
  );
}
