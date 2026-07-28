import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/di/injector.dart';
import '../services/api_client.dart';

class SessionsScreen extends StatelessWidget {
  const SessionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Tilovat va Savollar Tarixi'),
      ),
      body: FutureBuilder<List<dynamic>>(
        future: getIt<ApiClient>().sessions(),
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Text(
                'Xatolik yuz berdi: ${snapshot.error}',
                style: const TextStyle(color: Colors.red),
              ),
            );
          }
          final sessions = snapshot.data ?? const [];
          if (sessions.isEmpty) {
            return const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.history, size: 64, color: Colors.grey),
                  SizedBox(height: 16),
                  Text('Tarix bo\'sh. Hozircha hech qanday tilovat yoki savollar yo\'q.', style: TextStyle(color: Colors.grey)),
                ],
              ),
            );
          }

          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: sessions.length,
            separatorBuilder: (context, index) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final item = sessions[index] as Map<String, dynamic>;
              final isRecitation = item['type'] == 'recitation';
              final primary = item['primary']?.toString() ?? '';
              final secondary = item['secondary']?.toString() ?? '';
              final createdStr = item['created_at']?.toString() ?? '';
              
              DateTime? createdDate;
              String formattedTime = '';
              try {
                createdDate = DateTime.parse(createdStr).toLocal();
                formattedTime = DateFormat('dd.MM.yyyy HH:mm').format(createdDate);
              } catch (_) {
                formattedTime = createdStr;
              }

              return Card(
                elevation: 2,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: (isRecitation ? const Color(0xFF0F7A50) : const Color(0xFFD4AF37)).withValues(alpha: 0.1),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          isRecitation ? Icons.mic : Icons.favorite,
                          color: isRecitation ? const Color(0xFF0F7A50) : const Color(0xFFD4AF37),
                          size: 24,
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(
                                  isRecitation ? 'Qiroat Tekshiruvi' : 'Ruhiy Taskin',
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Color(0xFF1B4D3E)),
                                ),
                                Text(
                                  formattedTime,
                                  style: const TextStyle(fontSize: 11, color: Colors.grey),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            if (isRecitation) ...[
                              Text(
                                'Matn: "$primary"',
                                style: const TextStyle(fontSize: 13, fontStyle: FontStyle.italic),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                'Ball: $secondary',
                                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Color(0xFF0F7A50)),
                              ),
                            ] else ...[
                              Text(
                                'Savol: "$primary"',
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                'Javob: "$secondary"',
                                maxLines: 3,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 12, color: Colors.black87),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
