const io = require("socket.io")(process.env.PORT || 3000, {
  cors: { origin: "*" }
});

console.log("Server is Live!");

io.on("connection", (socket) => {
  console.log("Device connected: " + socket.id);
  socket.on("audio_data", (data) => {
    socket.broadcast.emit("receive_audio", data);
  });
});
