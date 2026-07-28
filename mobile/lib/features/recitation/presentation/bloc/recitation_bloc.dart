import 'dart:async';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../services/api_client.dart';
import '../../../../services/recitation_service.dart';
import 'recitation_event.dart';
import 'recitation_state.dart';

class RecitationBloc extends Bloc<RecitationEvent, RecitationState> {
  final RecitationService _recitationService;
  final ApiClient _apiClient;
  StreamSubscription? _recitationSubscription;

  RecitationBloc(this._recitationService, this._apiClient) : super(RecitationInitial()) {
    on<StartRecitationEvent>(_onStart);
    on<StopRecitationEvent>(_onStop);
    on<RecitationResultReceived>(_onResultReceived);
    on<RecitationErrorOccurred>(_onErrorOccurred);
  }

  Future<void> _onStart(StartRecitationEvent event, Emitter<RecitationState> emit) async {
    emit(RecitationInitial());
    try {
      final token = _apiClient.accessToken ?? '';
      final baseUrl = _apiClient.baseUrl;

      // Start audio recording and websocket stream
      await _recitationService.start(
        baseUrl: baseUrl,
        token: token,
        surahNumber: event.surahNumber,
        ayahNumber: event.ayahNumber,
      );
      
      // Subscribe to service events
      await _recitationSubscription?.cancel();
      _recitationSubscription = _recitationService.events.listen(
        (recEvent) {
          if (recEvent.error != null) {
            add(RecitationErrorOccurred(error: recEvent.error!));
          } else if (recEvent.type == 'result' || recEvent.transcription != null || recEvent.score != null) {
            add(RecitationResultReceived(result: {
              'transcription': recEvent.transcription ?? '',
              'score': recEvent.score ?? 0.0,
              'errors': recEvent.errors,
            }));
          }
        },
        onError: (err) {
          add(RecitationErrorOccurred(error: err.toString()));
        }
      );

      emit(RecitationRecording(surahNumber: event.surahNumber, ayahNumber: event.ayahNumber));
    } catch (e) {
      emit(RecitationErrorState(error: "Yozishni boshlab bo'lmadi: ${e.toString()}"));
    }
  }

  Future<void> _onStop(StopRecitationEvent event, Emitter<RecitationState> emit) async {
    if (state is RecitationRecording) {
      final current = state as RecitationRecording;
      try {
        await _recitationService.stopRecording();
        emit(RecitationProcessing(surahNumber: current.surahNumber, ayahNumber: current.ayahNumber));
      } catch (e) {
        emit(RecitationErrorState(error: "Yozishni to'xtatishda xatolik: ${e.toString()}"));
      }
    }
  }

  void _onResultReceived(RecitationResultReceived event, Emitter<RecitationState> emit) {
    emit(RecitationResultState(
      transcription: event.result['transcription'] as String? ?? '',
      score: (event.result['score'] as num?)?.toDouble() ?? 0.0,
      errors: event.result['errors'] as List<dynamic>? ?? const [],
    ));
  }

  void _onErrorOccurred(RecitationErrorOccurred event, Emitter<RecitationState> emit) {
    emit(RecitationErrorState(error: event.error));
  }

  @override
  Future<void> close() async {
    await _recitationSubscription?.cancel();
    await _recitationService.dispose();
    return super.close();
  }
}
