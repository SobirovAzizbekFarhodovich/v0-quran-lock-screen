import 'package:equatable/equatable.dart';

abstract class RecitationEvent extends Equatable {
  const RecitationEvent();

  @override
  List<Object?> get props => [];
}

class StartRecitationEvent extends RecitationEvent {
  final int surahNumber;
  final int ayahNumber;

  const StartRecitationEvent({required this.surahNumber, required this.ayahNumber});

  @override
  List<Object?> get props => [surahNumber, ayahNumber];
}

class StopRecitationEvent extends RecitationEvent {}

class RecitationResultReceived extends RecitationEvent {
  final Map<String, dynamic> result;

  const RecitationResultReceived({required this.result});

  @override
  List<Object?> get props => [result];
}

class RecitationErrorOccurred extends RecitationEvent {
  final String error;

  const RecitationErrorOccurred({required this.error});

  @override
  List<Object?> get props => [error];
}
