import 'dart:async';
import 'dart:math';
import 'package:web_socket_channel/web_socket_channel.dart';

class WebSocketClient {
  WebSocketChannel? _channel;
  Timer? _reconnectTimer;
  int _reconnectAttempts = 0;
  static const maxReconnectAttempts = 5;

  String? _lastUrl;
  String? _lastToken;

  final StreamController<dynamic> _streamController = StreamController<dynamic>.broadcast();
  Stream<dynamic> get stream => _streamController.stream;

  bool _isConnecting = false;

  void connect(String url, String token) {
    _lastUrl = url;
    _lastToken = token;
    _isConnecting = true;

    // Append token as query parameter or protocol header if needed
    final cleanUrl = url.contains('?') ? '$url&token=$token' : '$url?token=$token';

    try {
      _channel = WebSocketChannel.connect(
        Uri.parse(cleanUrl),
        protocols: ['v1.islamicai', 'bearer.$token'],
      );

      _channel!.stream.listen(
        (data) {
          _reconnectAttempts = 0; // Reset attempts on successful data
          _streamController.add(data);
        },
        onError: (error) {
          _onError(error);
        },
        onDone: () {
          _onDone();
        },
        cancelOnError: true,
      );
    } catch (e) {
      _onError(e);
    }
  }

  void sendAudio(List<int> chunk) {
    if (_channel != null && _channel!.sink != null) {
      _channel!.sink.add(chunk);
    }
  }

  void disconnect() {
    _reconnectTimer?.cancel();
    _channel?.sink.close();
    _channel = null;
    _reconnectAttempts = 0;
    _isConnecting = false;
  }

  void _onError(error) {
    print("WebSocket Error: $error");
    _scheduleReconnect();
  }

  void _onDone() {
    print("WebSocket Connection Closed");
    if (_isConnecting && _reconnectAttempts < maxReconnectAttempts) {
      _scheduleReconnect();
    }
  }

  void _scheduleReconnect() {
    _reconnectTimer?.cancel();
    final delay = Duration(
      seconds: pow(2, _reconnectAttempts).toInt(), // Exponential backoff: 1s, 2s, 4s, 8s, 16s
    );
    
    print("Scheduling WebSocket reconnect in ${delay.inSeconds} seconds (Attempt ${_reconnectAttempts + 1}/$maxReconnectAttempts)");

    _reconnectTimer = Timer(delay, () {
      _reconnectAttempts++;
      if (_lastUrl != null && _lastToken != null) {
        connect(_lastUrl!, _lastToken!);
      }
    });
  }
}
