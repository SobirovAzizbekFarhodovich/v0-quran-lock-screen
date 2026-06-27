import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../core/di/injector.dart';
import '../core/network/ws_client.dart';
import '../services/api_client.dart';
import '../services/recitation_service.dart';
import '../features/recitation/presentation/bloc/recitation_bloc.dart';
import '../features/recitation/presentation/bloc/recitation_event.dart';
import '../features/recitation/presentation/bloc/recitation_state.dart';
import '../features/recitation/presentation/widgets/mic_button.dart';
import '../features/recitation/presentation/widgets/waveform_widget.dart';
import '../features/recitation/presentation/widgets/score_widget.dart';
import '../features/recitation/presentation/widgets/tajweed_widget.dart';

class RecitationScreen extends StatefulWidget {
  const RecitationScreen({super.key});

  @override
  State<RecitationScreen> createState() => _RecitationScreenState();
}

class _RecitationScreenState extends State<RecitationScreen> {
  late RecitationBloc _bloc;
  int _selectedSurah = 1;
  int _selectedAyah = 1;

  @override
  void initState() {
    super.initState();
    // Instantiate new bloc instance, inject services manually or using GetIt
    final recitationService = getIt<WebSocketClient>() != null ? RecitationService() : RecitationService();
    _bloc = RecitationBloc(recitationService, getIt<ApiClient>());
  }

  @override
  Widget build(BuildContext context) {
    return BlocProvider<RecitationBloc>(
      create: (context) => _bloc,
      child: Scaffold(
        backgroundColor: const Color(0xFFF4F7F5),
        appBar: AppBar(
          title: const Text(
            'Qiroat Tekshiruvi',
            style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 0.5),
          ),
          elevation: 0,
          backgroundColor: const Color(0xFF0F7A50),
          foregroundColor: Colors.white,
        ),
        body: BlocBuilder<RecitationBloc, RecitationState>(
          builder: (context, state) {
            final isRecording = state is RecitationRecording;
            
            return SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Surah/Ayah Selector Card (only shown when not recording)
                  if (!isRecording) ...[
                    Card(
                      elevation: 4,
                      shadowColor: Colors.black.withOpacity(0.04),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                      color: Colors.white,
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Row(
                              children: [
                                Icon(Icons.menu_book_rounded, color: Color(0xFF0F7A50)),
                                SizedBox(width: 8),
                                Text(
                                  'Sura va Oyatni tanlang:',
                                  style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                    color: Color(0xFF1B4D3E),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 20),
                            Row(
                              children: [
                                Expanded(
                                  child: DropdownButtonFormField<int>(
                                    value: _selectedSurah,
                                    decoration: InputDecoration(
                                      labelText: 'Sura',
                                      filled: true,
                                      fillColor: Colors.grey.shade50,
                                      border: OutlineInputBorder(
                                        borderRadius: BorderRadius.circular(12),
                                        borderSide: BorderSide(color: Colors.grey.shade200),
                                      ),
                                      enabledBorder: OutlineInputBorder(
                                        borderRadius: BorderRadius.circular(12),
                                        borderSide: BorderSide(color: Colors.grey.shade100),
                                      ),
                                    ),
                                    items: List.generate(114, (i) => DropdownMenuItem(value: i + 1, child: Text('${i + 1}-Sura'))),
                                    onChanged: (val) {
                                      if (val != null) setState(() => _selectedSurah = val);
                                    },
                                  ),
                                ),
                                const SizedBox(width: 16),
                                Expanded(
                                  child: DropdownButtonFormField<int>(
                                    value: _selectedAyah,
                                    decoration: InputDecoration(
                                      labelText: 'Oyat',
                                      filled: true,
                                      fillColor: Colors.grey.shade50,
                                      border: OutlineInputBorder(
                                        borderRadius: BorderRadius.circular(12),
                                        borderSide: BorderSide(color: Colors.grey.shade200),
                                      ),
                                      enabledBorder: OutlineInputBorder(
                                        borderRadius: BorderRadius.circular(12),
                                        borderSide: BorderSide(color: Colors.grey.shade100),
                                      ),
                                    ),
                                    items: List.generate(50, (i) => DropdownMenuItem(value: i + 1, child: Text('${i + 1}-Oyat'))),
                                    onChanged: (val) {
                                      if (val != null) setState(() => _selectedAyah = val);
                                    },
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),
                  ],

                  if (isRecording) ...[
                    Card(
                      color: Colors.red.withOpacity(0.04),
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                        side: const BorderSide(color: Colors.red, width: 1),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          children: [
                            Text(
                              '$_selectedSurah-Sura, $_selectedAyah-Oyat tilovati tinglanmoqda...',
                              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: Colors.red),
                            ),
                            const SizedBox(height: 16),
                            const Text(
                              'Iltimos, oyatni ovoz chiqarib tilovat qiling.',
                              textAlign: TextAlign.center,
                              style: TextStyle(fontSize: 13, color: Colors.grey),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),
                  ],

                  // Pulsating Mic Button & Waveform
                  MicButton(
                    isRecording: isRecording,
                    onTap: () {
                      if (isRecording) {
                        _bloc.add(StopRecitationEvent());
                      } else {
                        _bloc.add(StartRecitationEvent(
                          surahNumber: _selectedSurah,
                          ayahNumber: _selectedAyah,
                        ));
                      }
                    },
                  ),
                  const SizedBox(height: 24),
                  
                  WaveformWidget(isRecording: isRecording),
                  const SizedBox(height: 32),

                  // Display result or error
                  if (state is RecitationResultState) ...[
                    ScoreWidget(score: state.score),
                    const SizedBox(height: 24),
                    
                    Text(
                      'Matn: "${state.transcription}"',
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 16, fontStyle: FontStyle.italic, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 24),
                    
                    TajweedWidget(errors: state.errors),
                  ],

                  if (state is RecitationErrorState) ...[
                    Card(
                      color: Colors.red.withOpacity(0.1),
                      elevation: 0,
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Row(
                          children: [
                            const Icon(Icons.error, color: Colors.red),
                            const SizedBox(width: 12),
                            Expanded(child: Text(state.error, style: const TextStyle(color: Colors.red))),
                          ],
                        ),
                      ),
                    )
                  ],
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  @override
  void dispose() {
    _bloc.close();
    super.dispose();
  }
}
