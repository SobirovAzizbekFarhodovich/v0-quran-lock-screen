import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../services/api_client.dart';
import 'spiritual_event.dart';
import 'spiritual_state.dart';

class SpiritualBloc extends Bloc<SpiritualEvent, SpiritualState> {
  final ApiClient _apiClient;

  SpiritualBloc(this._apiClient) : super(SpiritualInitial()) {
    on<SendSpiritualQueryEvent>(_onSendQuery);
    on<LoadSpiritualHistoryEvent>(_onLoadHistory);
  }

  Future<void> _onSendQuery(SendSpiritualQueryEvent event, Emitter<SpiritualState> emit) async {
    emit(SpiritualLoading());
    try {
      final answer = await _apiClient.spiritualQuery(event.message, event.language);
      emit(SpiritualMessageReceived(answer: answer));
    } catch (e) {
      emit(SpiritualError(message: "Javob olishda xatolik: ${e.toString()}"));
    }
  }

  Future<void> _onLoadHistory(LoadSpiritualHistoryEvent event, Emitter<SpiritualState> emit) async {
    emit(SpiritualLoading());
    try {
      final history = await _apiClient.sessions();
      emit(SpiritualHistoryLoaded(history: history));
    } catch (e) {
      emit(SpiritualError(message: "Tarixni yuklashda xatolik: ${e.toString()}"));
    }
  }
}
