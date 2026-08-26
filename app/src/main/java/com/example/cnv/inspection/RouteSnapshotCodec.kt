package com.example.cnv.inspection

import com.example.cnv.map.RouteEdge
import com.example.cnv.map.RouteNode
import com.example.cnv.map.RouteNodeType
import com.example.cnv.map.RouteSegment
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persist [RouteSnapshot] with Inspection Session (STEP 20-3).
 * History / Replay / Excel / HeatMap must restore this — not live Route.
 */
object RouteSnapshotCodec {

    fun encode(snapshot: RouteSnapshot): String {
        val root = JSONObject()
        root.put("routeId", snapshot.routeId)
        root.put("routeName", snapshot.routeName)
        root.put("routeVersion", snapshot.routeVersion)
        root.put("routeHash", snapshot.routeHash)
        root.put("startNodeId", snapshot.startNodeId)
        root.put("startSegmentId", snapshot.startSegmentId)
        root.put("capturedAtMs", snapshot.capturedAtMs)
        val nodes = JSONArray()
        snapshot.nodes.forEach { n ->
            nodes.put(
                JSONObject()
                    .put("id", n.id)
                    .put("type", n.type.name)
                    .put("label", n.label),
            )
        }
        root.put("nodes", nodes)
        val segments = JSONArray()
        snapshot.segments.forEach { s ->
            segments.put(
                JSONObject()
                    .put("id", s.id)
                    .put("fromNodeId", s.fromNodeId)
                    .put("toNodeId", s.toNodeId)
                    .put("lengthMm", s.lengthMm.toDouble()),
            )
        }
        root.put("segments", segments)
        val edges = JSONArray()
        snapshot.edges.forEach { e ->
            edges.put(
                JSONObject()
                    .put("id", e.id)
                    .put("fromNodeId", e.fromNodeId)
                    .put("toNodeId", e.toNodeId)
                    .put("segmentId", e.segmentId)
                    .put("preferred", e.preferred),
            )
        }
        root.put("edges", edges)
        val geometry = JSONArray()
        snapshot.segmentGeometry.values.forEach { g ->
            geometry.put(
                JSONObject()
                    .put("segmentId", g.segmentId)
                    .put("startX", g.start.x)
                    .put("startY", g.start.y)
                    .put("endX", g.end.x)
                    .put("endY", g.end.y),
            )
        }
        root.put("segmentGeometry", geometry)
        return root.toString()
    }

    fun decode(json: String?): RouteSnapshot? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            val nodes = ArrayList<RouteNode>()
            val nodesArr = root.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until nodesArr.length()) {
                val o = nodesArr.getJSONObject(i)
                nodes += RouteNode(
                    id = o.getString("id"),
                    type = runCatching { RouteNodeType.valueOf(o.getString("type")) }
                        .getOrDefault(RouteNodeType.WAYPOINT),
                    label = o.optString("label", o.getString("id")),
                )
            }
            val segments = ArrayList<RouteSegment>()
            val segArr = root.optJSONArray("segments") ?: JSONArray()
            for (i in 0 until segArr.length()) {
                val o = segArr.getJSONObject(i)
                segments += RouteSegment(
                    id = o.getString("id"),
                    fromNodeId = o.getString("fromNodeId"),
                    toNodeId = o.getString("toNodeId"),
                    lengthMm = o.getDouble("lengthMm").toFloat(),
                )
            }
            val edges = ArrayList<RouteEdge>()
            val edgeArr = root.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until edgeArr.length()) {
                val o = edgeArr.getJSONObject(i)
                edges += RouteEdge(
                    id = o.getString("id"),
                    fromNodeId = o.getString("fromNodeId"),
                    toNodeId = o.getString("toNodeId"),
                    segmentId = o.getString("segmentId"),
                    preferred = o.optBoolean("preferred", true),
                )
            }
            val geometry = LinkedHashMap<String, com.example.cnv.route.CoordinateMapper.SegmentGeometry>()
            val geomArr = root.optJSONArray("segmentGeometry") ?: JSONArray()
            for (i in 0 until geomArr.length()) {
                val o = geomArr.getJSONObject(i)
                val id = o.getString("segmentId")
                geometry[id] = com.example.cnv.route.CoordinateMapper.SegmentGeometry(
                    segmentId = id,
                    start = com.example.cnv.route.WorldCoordinate(
                        x = o.getDouble("startX"),
                        y = o.getDouble("startY"),
                    ),
                    end = com.example.cnv.route.WorldCoordinate(
                        x = o.getDouble("endX"),
                        y = o.getDouble("endY"),
                    ),
                )
            }
            RouteSnapshot(
                routeId = root.getString("routeId"),
                routeName = root.optString("routeName", ""),
                routeVersion = root.optString("routeVersion", ""),
                routeHash = root.optString("routeHash", ""),
                nodes = nodes,
                segments = segments,
                edges = edges,
                startNodeId = root.optString("startNodeId", ""),
                startSegmentId = root.optString("startSegmentId", ""),
                capturedAtMs = root.optLong("capturedAtMs", 0L),
                segmentGeometry = geometry,
            )
        }.getOrNull()
    }
}
