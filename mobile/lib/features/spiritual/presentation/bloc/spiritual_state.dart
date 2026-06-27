import 'package:equatable/equatable.dart';
import '../../../../models/api_models.dart';

abstract class SpiritualState extends Equatable {
  const SpiritualState();

  @override
  List<Object?> get props => [];
}

class SpiritualInitial extends SpiritualState {}

class SpiritualLoading extends SpiritualState {}

class SpiritualMessageReceived extends SpiritualState {
  final SpiritualAnswer answer;

  const SpiritualMessageReceived({required this.answer});

  @override
  List<Object?> get props => [answer];
}

class SpiritualHistoryLoaded extends SpiritualState {
  final List<dynamic> history;

  const SpiritualHistoryLoaded({required this.history});

  @override
  List<Object?> get props => [history];
}

class SpiritualError extends SpiritualState {
  final String message;

  const SpiritualError({required this.message});

  @override
  List<Object?> get props => [message];
}
