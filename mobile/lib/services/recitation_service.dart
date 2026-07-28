import 'dart:async';
import 'dart:convert';

import 'package:record/record.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../models/api_models.dart';

class RecitationService {
  RecitationService({AudioRecorder? recorder}) : _recorder = recorder ?? AudioRecorder();

  final AudioRecorder _recorder;
  final StreamController<RecitationEvent> _events = StreamController<RecitationEvent>.broadcast();
  WebSocketChannel? _channel;
  StreamSubscription<List<int>>? _audioSubscription;
  StreamSubscription<dynamic>? _channelSubscription;

  Stream<RecitationEvent> get events => _events.stream;

  Future<void> start({required String baseUrl, required String token, required int surahNumber, required int ayahNumber}) async {
    await stop();
    if (!await _recorder.hasPermission()) {
      throw StateError('Microphone permission is required');
    }
    final wsBase = baseUrl.replaceFirst('http://', 'ws://').replaceFirst('https://', 'wss://');
    final uri = Uri.parse('$wsBase/ws/recitation').replace(queryParameters: {
      'token': token,
      'surah': '$surahNumber',
      'ayah': '$ayahNumber',
    });
    _channel = WebSocketChannel.connect(uri, protocols: ['v1.islamicai']);
    _channelSubscription = _channel!.stream.listen((data) {
      if (data is! String) return;
      final json = jsonDecode(data) as Map<String, dynamic>;
      final event = RecitationEvent.fromJson(json);
      _events.add(event);
      if (event.type == 'result' && event.finalResult) {
        Future.microtask(_closeConnection);
      }
    }, onError: _events.addError);

    final stream = await _recorder.startStream(const RecordConfig(encoder: AudioEncoder.pcm16bits, sampleRate: 16000, numChannels: 1));
    _audioSubscription = stream.listen((chunk) => _channel?.sink.add(chunk));
  }

  Future<void> stopRecording() async {
    await _audioSubscription?.cancel();
    _audioSubscription = null;
    if (await _recorder.isRecording()) {
      await _recorder.stop();
    }
    _channel?.sink.add(jsonEncode({'type': 'stop'}));
  }

  Future<void> stop() async {
    await _audioSubscription?.cancel();
    _audioSubscription = null;
    if (await _recorder.isRecording()) {
      await _recorder.stop();
    }
    await _closeConnection();
  }

  Future<void> _closeConnection() async {
    await _channelSubscription?.cancel();
    _channelSubscription = null;
    await _channel?.sink.close();
    _channel = null;
  }

  Future<void> dispose() async {
    await stop();
    await _events.close();
  }
}
