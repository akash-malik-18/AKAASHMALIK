const io = require("socket.io")(process.env.PORT || 3000, {
  cors: { origin: "*" }
});
io.on("connection", (socket) => {
  socket.on("audio_data", (data) => {
    socket.broadcast.emit("receive_audio", data);
  });
});
