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

  Stream<RecitationEvent> get events => _events.stream;

  Future<void> start({required String baseUrl, required String token}) async {
    if (!await _recorder.hasPermission()) {
      throw StateError('Microphone permission is required');
    }
    final wsBase = baseUrl.replaceFirst('http://', 'ws://').replaceFirst('https://', 'wss://');
    _channel = WebSocketChannel.connect(Uri.parse('$wsBase/api/v1/ws/recitation'), protocols: ['bearer.$token']);
    _channel!.stream.listen((data) {
      final json = jsonDecode(data as String) as Map<String, dynamic>;
      _events.add(RecitationEvent.fromJson(json));
    }, onError: _events.addError);

    final stream = await _recorder.startStream(const RecordConfig(encoder: AudioEncoder.pcm16bits, sampleRate: 16000, numChannels: 1));
    _audioSubscription = stream.listen((chunk) => _channel?.sink.add(chunk));
  }

  Future<void> stop() async {
    await _audioSubscription?.cancel();
    _audioSubscription = null;
    if (await _recorder.isRecording()) {
      await _recorder.stop();
    }
    await _channel?.sink.close();
    _channel = null;
  }

  Future<void> dispose() async {
    await stop();
    await _events.close();
  }
}
