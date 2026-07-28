package metrics

import "github.com/prometheus/client_golang/prometheus"

var (
	ActiveWebSockets = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "islamic_ai_active_websockets",
		Help: "Current active recitation WebSocket connections.",
	})

	RecitationChunksTotal = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "islamic_ai_recitation_chunks_total",
		Help: "Total audio chunks accepted from recitation WebSocket clients.",
	})

	RecitationResultsTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "islamic_ai_recitation_results_total",
		Help: "Total recitation analysis results delivered to WebSocket clients.",
	}, []string{"status"})

	RecitationResultLatency = prometheus.NewHistogram(prometheus.HistogramOpts{
		Name:    "islamic_ai_recitation_result_latency_seconds",
		Help:    "Time from recitation session start to first delivered AI result.",
		Buckets: prometheus.DefBuckets,
	})

	RAGQueryDuration = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "islamic_ai_rag_query_duration_seconds",
		Help:    "Duration of RAG query proxy requests.",
		Buckets: prometheus.DefBuckets,
	}, []string{"status"})
)

func init() {
	prometheus.MustRegister(
		ActiveWebSockets,
		RecitationChunksTotal,
		RecitationResultsTotal,
		RecitationResultLatency,
		RAGQueryDuration,
	)
}
