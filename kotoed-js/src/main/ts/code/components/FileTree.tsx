import * as React from "react";

import { Classes,  Tree } from "@blueprintjs/core";
import {FileNodes, FileTreeNode, makeLoadingNode} from "../util/filetree";

export interface FileTreeProps {
    loading: boolean
    nodes: FileNodes
}

export interface FileTreeCallbacks {
    onDirExpand(path: number[]): void
    onDirCollapse(path: number[]): void
    onFileSelect(path: number[]): void
}

export default class FileTree extends React.Component<FileTreeProps & FileTreeCallbacks, {}> {
    // override @PureRender because nodes are not a primitive type and therefore aren't included in
    // shallow prop comparison
    public shouldComponentUpdate() {
        return true;
    }

    private onNodeClick = (nodeData: FileTreeNode, path: number[]) => {
        if (nodeData.kind === "loading")
            return;
        if (nodeData.type === "file" && !nodeData.isSelected) {
            this.props.onFileSelect(path);
        } else if (nodeData.type === "directory" && nodeData.isExpanded) {
            this.props.onDirCollapse(path);
        } else if (nodeData.type === "directory" && !nodeData.isExpanded) {
            this.props.onDirExpand(path);
        }
    };

    private onNodeCollapse = (nodeData: FileTreeNode, path: number[]) => {
        if (nodeData.kind === "file" && nodeData.type === "directory") {
            this.props.onDirCollapse(path);
        }
    };

    private onNodeExpand = (nodeData: FileTreeNode, path: number[]) => {
        if (nodeData.kind === "file" && nodeData.type === "directory") {
            this.props.onDirExpand(path);
        }
    };

    render() {
        return (
            <Tree
                contents={this.props.loading ?
                    [makeLoadingNode(() => 0)] : // We don't care about id since it's only one node here
                    this.props.nodes}
                onNodeClick={this.onNodeClick}
                onNodeCollapse={this.onNodeCollapse}
                onNodeExpand={this.onNodeExpand}
                className={Classes.ELEVATION_0}
            />
        );
    }
}