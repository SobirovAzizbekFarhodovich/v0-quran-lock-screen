import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../core/di/injector.dart';
import '../services/api_client.dart';
import '../features/spiritual/presentation/bloc/spiritual_bloc.dart';
import '../features/spiritual/presentation/bloc/spiritual_event.dart';
import '../features/spiritual/presentation/bloc/spiritual_state.dart';
import '../features/spiritual/presentation/widgets/chat_bubble.dart';
import '../features/spiritual/presentation/widgets/source_card.dart';
import '../features/spiritual/presentation/widgets/typing_indicator.dart';

class ChatMessage {
  final String text;
  final bool isUser;
  final List<dynamic>? sources;

  ChatMessage({
    required this.text,
    required this.isUser,
    this.sources,
  });
}

class SpiritualScreen extends StatefulWidget {
  const SpiritualScreen({super.key});

  @override
  State<SpiritualScreen> createState() => _SpiritualScreenState();
}

class _SpiritualScreenState extends State<SpiritualScreen> {
  late SpiritualBloc _bloc;
  final _controller = TextEditingController();
  final List<ChatMessage> _messages = [];
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _bloc = SpiritualBloc(getIt<ApiClient>());
    
    // Add initial greeting message
    _messages.add(ChatMessage(
      text: "Assalomu alaykum. Bugun o'zingizni qanday his qilyapsiz? Ruhoniy holatingiz haqida yozing, kitoblardan taskin izlaymiz.",
      isUser: false,
    ));
  }

  @override
  Widget build(BuildContext context) {
    return BlocProvider<SpiritualBloc>(
      create: (context) => _bloc,
      child: Scaffold(
        backgroundColor: const Color(0xFFF4F7F5),
        appBar: AppBar(
          title: const Text(
            'Ruhiy Taskin',
            style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 0.5),
          ),
          elevation: 0,
          backgroundColor: const Color(0xFF0F7A50),
          foregroundColor: Colors.white,
        ),
        body: BlocConsumer<SpiritualBloc, SpiritualState>(
          listener: (context, state) {
            if (state is SpiritualMessageReceived) {
              setState(() {
                _messages.add(ChatMessage(
                  text: state.answer.answer,
                  isUser: false,
                  sources: state.answer.sources,
                ));
              });
              _scrollToBottom();
            } else if (state is SpiritualError) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(state.message), 
                  backgroundColor: Colors.redAccent,
                  behavior: SnackBarBehavior.floating,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              );
            }
          },
          builder: (context, state) {
            final isLoading = state is SpiritualLoading;

            return Column(
              children: [
                // Chat Messages List
                Expanded(
                  child: ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
                    itemCount: _messages.length + (isLoading ? 1 : 0),
                    itemBuilder: (context, index) {
                      if (index == _messages.length) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: 8.0),
                          child: TypingIndicator(),
                        );
                      }

                      final msg = _messages[index];
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          ChatBubble(text: msg.text, isUser: msg.isUser),
                          if (!msg.isUser && msg.sources != null && msg.sources!.isNotEmpty) ...[
                            const SizedBox(height: 4),
                            const Padding(
                              padding: EdgeInsets.only(left: 14, bottom: 6),
                              child: Text(
                                'Manbalar:',
                                style: TextStyle(
                                  fontSize: 12, 
                                  fontWeight: FontWeight.bold, 
                                  color: Color(0xFF1B4D3E),
                                ),
                              ),
                            ),
                            SizedBox(
                              height: 76,
                              child: ListView.builder(
                                scrollDirection: Axis.horizontal,
                                padding: const EdgeInsets.only(left: 12, bottom: 8),
                                itemCount: msg.sources!.length,
                                itemBuilder: (context, sIdx) {
                                  final source = msg.sources![sIdx];
                                  return SourceCard(
                                    book: source['book']?.toString() ?? 'Kitob',
                                    chapter: source['chapter']?.toString() ?? '',
                                  );
                                },
                              ),
                            ),
                          ],
                        ],
                      );
                    },
                  ),
                ),

                // Input field
                Container(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.04),
                        blurRadius: 10,
                        offset: const Offset(0, -4),
                      )
                    ],
                    borderRadius: const BorderRadius.only(
                      topLeft: Radius.circular(24),
                      topRight: Radius.circular(24),
                    ),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _controller,
                          minLines: 1,
                          maxLines: 4,
                          decoration: InputDecoration(
                            hintText: 'Holatingizni yozing...',
                            hintStyle: TextStyle(color: Colors.grey.shade400),
                            contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                            filled: true,
                            fillColor: Colors.grey.shade50,
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: BorderSide(color: Colors.grey.shade200),
                            ),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: BorderSide(color: Colors.grey.shade100),
                            ),
                            focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: const BorderSide(color: Color(0xFF0F7A50), width: 1.2),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      CircleAvatar(
                        backgroundColor: const Color(0xFF0F7A50),
                        radius: 24,
                        child: IconButton(
                          icon: const Icon(Icons.send_rounded, color: Colors.white, size: 20),
                          onPressed: isLoading ? null : _sendMessage,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  void _sendMessage() {
    final text = _controller.text.trim();
    if (text.isNotEmpty) {
      setState(() {
        _messages.add(ChatMessage(text: text, isUser: true));
      });
      _controller.clear();
      _scrollToBottom();
      
      // Fetch language setting from local profile or default to 'uz'
      _bloc.add(SendSpiritualQueryEvent(message: text, language: 'uz'));
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    _bloc.close();
    super.dispose();
  }
}
