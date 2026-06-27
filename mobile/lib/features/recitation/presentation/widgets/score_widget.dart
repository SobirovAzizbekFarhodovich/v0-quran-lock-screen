import 'package:flutter/material.dart';

class ScoreWidget extends StatelessWidget {
  final double score;

  const ScoreWidget({super.key, required this.score});

  Color _getScoreColor(double val) {
    if (val >= 90.0) return const Color(0xFF0F7A50);
    if (val >= 80.0) return const Color(0xFFD4AF37);
    return Colors.red;
  }

  @override
  Widget build(BuildContext context) {
    final scoreColor = _getScoreColor(score);
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Stack(
            alignment: Alignment.center,
            children: [
              SizedBox(
                height: 120,
                width: 120,
                child: CircularProgressIndicator(
                  value: score / 100.0,
                  strokeWidth: 10,
                  backgroundColor: scoreColor.withOpacity(0.15),
                  valueColor: AlwaysStoppedAnimation<Color>(scoreColor),
                ),
              ),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    score.toStringAsFixed(1),
                    style: TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.bold,
                      color: scoreColor,
                    ),
                  ),
                  const Text(
                    'ball',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}
